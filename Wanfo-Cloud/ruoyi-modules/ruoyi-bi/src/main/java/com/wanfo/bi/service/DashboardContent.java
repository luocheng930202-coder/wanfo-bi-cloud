package com.wanfo.bi.service;

import java.util.Base64;

/** Small embedded image/text assets; no HTML, SVG, remote URLs or executable content. */
public final class DashboardContent {
    private DashboardContent() {}
    public record Decoration(String color,Integer stroke,String pattern,Integer radius,String direction) {}
    public record ContainerStyle(String background,String borderColor,Integer stroke,Integer radius,Integer padding) {}
    public record Content(String text,String image,Decoration decoration,ContainerStyle container) {
        public Content(String text,String image){this(text,image,null,null);}
        public Content(String text,String image,Decoration decoration){this(text,image,decoration,null);}
    }
    public static ContainerStyle container(ContainerStyle c){
        String bg=c==null || c.background()==null?"#16243A":c.background(),border=c==null || c.borderColor()==null?"#38BDF8":c.borderColor();int stroke=c==null || c.stroke()==null?1:c.stroke(),radius=c==null || c.radius()==null?8:c.radius(),padding=c==null || c.padding()==null?12:c.padding();
        if(!(bg.equals("transparent") || bg.matches("#[0-9a-fA-F]{6}")) || !border.matches("#[0-9a-fA-F]{6}") || stroke<0 || stroke>12 || radius<0 || radius>80 || padding<0 || padding>40)throw new IllegalArgumentException("容器背景、边框或内边距无效");
        return new ContainerStyle(bg,border,stroke,radius,padding);
    }
    public static Decoration decoration(Decoration d){
        String color=d==null || d.color()==null?"#38BDF8":d.color();int stroke=d==null || d.stroke()==null?2:d.stroke(),radius=d==null || d.radius()==null?8:d.radius();String pattern=d==null || d.pattern()==null?"solid":d.pattern(),direction=d==null || d.direction()==null?"horizontal":d.direction();
        if(!color.matches("#[0-9a-fA-F]{6}") || stroke<1 || stroke>12 || radius<0 || radius>80 || !java.util.Set.of("solid","dashed","dotted").contains(pattern) || !java.util.Set.of("horizontal","vertical").contains(direction))throw new IllegalArgumentException("装饰样式无效：颜色、线宽、线型、圆角或方向超出范围");
        return new Decoration(color,stroke,pattern,radius,direction);
    }
    public static Content normalize(String type,Content c) {
        if(type.equals("container")){if(c!=null && (c.text()!=null || c.image()!=null || c.decoration()!=null))throw new IllegalArgumentException("容器不能混用其他内容");return new Content(null,null,null,container(c==null?null:c.container()));}
        if(c!=null && c.container()!=null)throw new IllegalArgumentException("当前组件不支持容器样式");
        if(type.equals("border") || type.equals("line")){if(c!=null && (c.text()!=null || c.image()!=null))throw new IllegalArgumentException("装饰组件不能携带文本或图片");return new Content(null,null,decoration(c==null?null:c.decoration()));}
        if(c!=null && c.decoration()!=null)throw new IllegalArgumentException("当前组件不支持装饰内容");
        if(type.equals("chart") || type.equals("kpi")){if(c!=null)throw new IllegalArgumentException("数据组件不能携带静态内容");return null;}
        if(type.equals("text")){
            if(c!=null && c.image()!=null)throw new IllegalArgumentException("文本不能包含图片内容");
            String text=c==null || c.text()==null?"":c.text();if(text.length()>2000)throw new IllegalArgumentException("文本最多 2000 字符");return new Content(text,null);
        }
        if(c!=null && c.text()!=null)throw new IllegalArgumentException("图片不能包含文本内容");
        String image=c==null || c.image()==null?"":c.image();
        if(!image.isEmpty()) {
            if(image.length()>350000 || !image.matches("data:image/(png|jpeg|webp);base64,[A-Za-z0-9+/]+={0,2}"))throw new IllegalArgumentException("仅支持不超过 256KB 的 PNG/JPEG/WebP 图片");
            byte[] bytes;try{bytes=Base64.getDecoder().decode(image.substring(image.indexOf(',')+1));}catch(IllegalArgumentException ex){throw new IllegalArgumentException("图片编码无效");}
            boolean png=image.startsWith("data:image/png;") && bytes.length>=8 && bytes[0]==(byte)137 && bytes[1]==80 && bytes[2]==78 && bytes[3]==71 && bytes[4]==13 && bytes[5]==10 && bytes[6]==26 && bytes[7]==10;
            boolean jpeg=image.startsWith("data:image/jpeg;") && bytes.length>=3 && bytes[0]==(byte)255 && bytes[1]==(byte)216 && bytes[2]==(byte)255;
            boolean webp=image.startsWith("data:image/webp;") && bytes.length>=12 && bytes[0]==82 && bytes[1]==73 && bytes[2]==70 && bytes[3]==70 && bytes[8]==87 && bytes[9]==69 && bytes[10]==66 && bytes[11]==80;
            if(bytes.length>262144 || !(png || jpeg || webp))throw new IllegalArgumentException("图片内容与格式不匹配");
        }
        return new Content(null,image);
    }
}
