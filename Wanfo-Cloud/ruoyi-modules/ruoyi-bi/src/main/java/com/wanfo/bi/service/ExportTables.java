package com.wanfo.bi.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;

/** CSV (UTF-8 BOM), streaming-friendly Excel, and table PDF. Formula-looking values are prefixed with a quote. */
public final class ExportTables {
    public record Merge(int firstRow,int lastRow,int firstCol,int lastCol) { }
    public record Grid(List<List<Object>> cells,List<Merge> merges,int headerRows,int frozenColumns,List<Integer> summaryRows,List<Integer> summaryColumns) { }
    public record Sheet(String name, List<String> columns, List<Map<String,Object>> rows,Grid grid) {
        public Sheet(String name,List<String> columns,List<Map<String,Object>> rows){this(name,columns,rows,null);}
        public int rowCount(){return grid==null?rows==null?0:rows.size():grid.cells().size()-grid.headerRows();}
    }
    public static final int MAX_ROWS_CSV = 100_000;
    public static final int MAX_ROWS_XLSX = 10_000;
    public static final int MAX_ROWS_PDF = 5_000;
    private ExportTables() { }
    public static byte[] csv(Sheet sheet) {
        if (sheet == null) throw new IllegalArgumentException("导出内容无效");
        var out = new ByteArrayOutputStream();
        writeCsv(sheet, out, MAX_ROWS_CSV);
        return out.toByteArray();
    }
    public static void writeCsv(Sheet sheet, OutputStream out, int maxRows) {
        if (sheet == null) throw new IllegalArgumentException("导出内容无效");
        try {
            out.write(new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});
            List<String> columns = columns(sheet, maxRows);
            writeCsvLine(out, columns);
            int count = 0;
            for (var row : rows(sheet, maxRows)) {
                if (++count > maxRows) throw new IllegalArgumentException("导出行数超过限制");
                var line = new StringBuilder();
                for (int i = 0; i < columns.size(); i++) {
                    if (i > 0) line.append(',');
                    line.append(csvCell(text(row.get(columns.get(i)))));
                }
                out.write(line.append("\r\n").toString().getBytes(StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            throw new IllegalStateException("无法生成 CSV 文件");
        }
    }
    public static byte[] xlsx(List<Sheet> sheets) {
        if (sheets == null || sheets.isEmpty() || sheets.size() > 32) throw new IllegalArgumentException("Excel 至少需要一个数据表，最多 32 个");
        try (var workbook = new SXSSFWorkbook(200)) {
            for (int i = 0; i < sheets.size(); i++) {
                var sheet = sheets.get(i);
                var tab = workbook.createSheet(sheetName(sheet.name(), i));
                if(sheet.grid()!=null){writeGrid(workbook,tab,sheet.grid());continue;}
                List<String> columns = columns(sheet, MAX_ROWS_XLSX);
                Row header = tab.createRow(0);
                for (int c = 0; c < columns.size(); c++) header.createCell(c).setCellValue(columns.get(c));
                int r = 1;
                for (var data : rows(sheet, MAX_ROWS_XLSX)) {
                    Row row = tab.createRow(r++);
                    for (int c = 0; c < columns.size(); c++) {
                        Object value = data.get(columns.get(c));
                        if (value instanceof Number n && !(n instanceof Double d && (d.isNaN() || d.isInfinite()))
                                && !(n instanceof Float f && (f.isNaN() || f.isInfinite())))
                            row.createCell(c).setCellValue(n.doubleValue());
                        else row.createCell(c).setCellValue(text(value));
                    }
                }
            }
            var bytes = new ByteArrayOutputStream();
            workbook.write(bytes);
            workbook.dispose();
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("无法生成 Excel 文件");
        }
    }
    private static void writeGrid(SXSSFWorkbook workbook,org.apache.poi.ss.usermodel.Sheet tab,Grid grid) {
        if(grid.cells().size()>MAX_ROWS_XLSX || grid.cells().stream().anyMatch(row->row.size()>256))throw new IllegalArgumentException("透视导出超过 10000 行或 256 列，请缩小范围");
        var bold=workbook.createFont();bold.setBold(true);
        var header=workbook.createCellStyle();header.setFont(bold);header.setFillForegroundColor(org.apache.poi.ss.usermodel.IndexedColors.PALE_BLUE.getIndex());header.setFillPattern(org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND);header.setWrapText(true);
        var summary=workbook.createCellStyle();summary.setFont(bold);summary.setFillForegroundColor(org.apache.poi.ss.usermodel.IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());summary.setFillPattern(org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND);
        var summaries=new java.util.HashSet<>(grid.summaryRows());var summaryColumns=new java.util.HashSet<>(grid.summaryColumns());
        for(int r=0;r<grid.cells().size();r++){
            var row=tab.createRow(r);row.setHeightInPoints(r==0?44:r<grid.headerRows()?30:22);var values=grid.cells().get(r);
            for(int c=0;c<values.size();c++){
                var cell=row.createCell(c);Object value=values.get(c);
                if(value instanceof java.math.BigDecimal number && excelNumber(number))cell.setCellValue(number.doubleValue());
                else cell.setCellValue(text(value));
                if(r<grid.headerRows())cell.setCellStyle(header);else if(summaries.contains(r) || summaryColumns.contains(c))cell.setCellStyle(summary);
            }
        }
        for(var merge:grid.merges())tab.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(merge.firstRow(),merge.lastRow(),merge.firstCol(),merge.lastCol()));
        int width=grid.cells().stream().mapToInt(List::size).max().orElse(0);
        for(int c=0;c<width;c++)tab.setColumnWidth(c,(c<grid.frozenColumns()?20:18)*256);
        tab.createFreezePane(grid.frozenColumns(),grid.headerRows());
    }
    private static boolean excelNumber(java.math.BigDecimal number){
        double value=number.doubleValue();
        return number.precision()<=15 && Double.isFinite(value) && (number.signum()==0 || Math.abs(value)>=Double.MIN_NORMAL)
                && java.math.BigDecimal.valueOf(value).compareTo(number)==0;
    }
    public static byte[] pdf(Sheet sheet) {
        if (sheet == null) throw new IllegalArgumentException("导出内容无效");
        List<String> columns = columns(sheet, MAX_ROWS_PDF);
        List<Map<String,Object>> dataRows = rows(sheet, MAX_ROWS_PDF);
        try {
            var out = new ByteArrayOutputStream();
            var doc = new Document(PageSize.A4.rotate(), 24, 24, 36, 36);
            PdfWriter.getInstance(doc, out);
            doc.open();
            Font title = pdfFont(12, Font.BOLD);
            Font body = pdfFont(9, Font.NORMAL);
            doc.add(new Paragraph(sheet.name() == null || sheet.name().isBlank() ? "数据导出" : sheet.name(), title));
            doc.add(new Paragraph(" ", body));
            var table = new PdfPTable(Math.max(1, columns.size()));
            table.setWidthPercentage(100);
            for (String column : columns) table.addCell(pdfCell(text(column), body, true));
            for (var row : dataRows) {
                for (String column : columns) table.addCell(pdfCell(text(row.get(column)), body, false));
            }
            doc.add(table);
            doc.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("无法生成 PDF 文件");
        }
    }
    public static String fileName(String name, String format) {
        String base = name == null || name.isBlank() ? "export" : name.trim();
        base = base.replaceAll("[\\\\/:*?\"<>|\\u0000-\\u001f]", "_");
        if (base.length() > 180) base = base.substring(0, 180);
        if (base.isBlank()) base = "export";
        String ext = "csv".equals(format) ? ".csv" : "pdf".equals(format) ? ".pdf" : ".xlsx";
        return base + ext;
    }
    static String text(Object value) {
        if (value == null) return "";
        if (value instanceof Number n) return number(n);
        String raw = String.valueOf(value);
        StringBuilder clean = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == 9 || c == 10 || c == 13 || c >= 32) clean.append(c);
        }
        String s = clean.toString();
        if (s.isEmpty()) return s;
        int cp = s.codePointAt(0);
        if (cp == '=' || cp == '+' || cp == '-' || cp == '@' || cp == '\t' || cp == '\r' || cp == '\n'
                || cp == 0xFF1D || cp == 0xFF0B || cp == 0xFF0D || cp == 0xFF20) return "'" + s;
        return s;
    }
    static String sheetName(String name, int index) {
        String s = name == null || name.isBlank() ? "数据" : name.trim();
        s = s.replaceAll("[\\\\/:?*\\[\\]]", "_");
        if (s.length() > 28) s = s.substring(0, 28);
        if (s.isBlank()) s = "数据";
        return s + (index == 0 ? "" : "_" + (index + 1));
    }
    private static List<String> columns(Sheet sheet, int maxRows) {
        if (sheet.columns() == null) return List.of();
        if (sheet.columns().size() > 64) throw new IllegalArgumentException("导出列数超过限制");
        return sheet.columns().stream().map(c -> c == null ? "" : String.valueOf(c)).toList();
    }
    private static List<Map<String,Object>> rows(Sheet sheet, int maxRows) {
        if (sheet.rows() == null) return List.of();
        if (sheet.rows().size() > maxRows) throw new IllegalArgumentException("导出行数超过限制");
        return sheet.rows();
    }
    private static void writeCsvLine(OutputStream out, List<String> columns) throws IOException {
        var line = new StringBuilder();
        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) line.append(',');
            line.append(csvCell(text(columns.get(i))));
        }
        out.write(line.append("\r\n").toString().getBytes(StandardCharsets.UTF_8));
    }
    private static String csvCell(String value) {
        if (value.indexOf('"') >= 0 || value.indexOf(',') >= 0 || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0)
            return '"' + value.replace("\"", "\"\"") + '"';
        return value;
    }
    private static String number(Number n) {
        if (n instanceof Double d && (d.isNaN() || d.isInfinite())) return "";
        if (n instanceof Float f && (f.isNaN() || f.isInfinite())) return "";
        String s = n.toString();
        if ((n instanceof Double || n instanceof Float) && s.endsWith(".0")) return s.substring(0, s.length() - 2);
        return s;
    }
    private static Font pdfFont(int size, int style) {
        try {
            BaseFont base = BaseFont.createFont("STSong-Light", "UniGB-UCS2-H", BaseFont.NOT_EMBEDDED);
            return new Font(base, size, style);
        } catch (Exception ignored) {
            return new Font(Font.HELVETICA, size, style);
        }
    }
    private static PdfPCell pdfCell(String value, Font font, boolean header) {
        var cell = new PdfPCell(new Paragraph(value == null ? "" : value, font));
        cell.setHorizontalAlignment(Element.ALIGN_LEFT);
        if (header) cell.setGrayFill(0.9f);
        return cell;
    }
    static String col(int index) {
        StringBuilder s = new StringBuilder();
        for (int n = index; n >= 0; n = n / 26 - 1) s.insert(0, (char) ('A' + n % 26));
        return s.toString();
    }
}
