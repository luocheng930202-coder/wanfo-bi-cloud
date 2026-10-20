package com.wanfo.bi.service;

import java.util.*;

/** One-level containers with absolute scene coordinates and contiguous layer blocks. */
public final class DashboardContainers {
    private DashboardContainers() {}
    public static void validate(List<DashboardService.Panel> panels,String kind){
        var byKey=new HashMap<String,DashboardService.Panel>();
        for(var p:panels){if(byKey.put(p.key(),p)!=null)throw new IllegalArgumentException("组件标识重复");}
        for(int i=0;i<panels.size();i++){
            var p=panels.get(i);boolean container="container".equals(p.componentType());
            if(container || p.parentKey()!=null){
                if(!"screen".equals(kind) || p.groupKey()!=null)throw new IllegalArgumentException("容器及成员仅支持大屏且须先解组");
            }
            if(container){
                if(p.parentKey()!=null)throw new IllegalArgumentException("当前不支持嵌套容器");
                int pad=p.content().container().padding();
                if(p.w()==null || p.h()==null || p.w()<2*pad+20 || p.h()<2*pad+20)throw new IllegalArgumentException("容器尺寸不足以容纳内边距");
            }
            if(p.parentKey()==null)continue;
            var parent=byKey.get(p.parentKey());
            if(parent==null || parent==p || !"container".equals(parent.componentType()) || parent.parentKey()!=null)throw new IllegalArgumentException("容器父级无效");
            int pi=panels.indexOf(parent),pad=parent.content().container().padding();
            if(pi>=i)throw new IllegalArgumentException("容器须位于成员之前");
            for(int n=pi+1;n<i;n++)if(!Objects.equals(panels.get(n).parentKey(),parent.key()))throw new IllegalArgumentException("容器成员须连续排列");
            if(p.x()==null || p.y()==null || p.w()==null || p.h()==null || parent.x()==null || parent.y()==null || parent.w()==null || parent.h()==null
                || (long)p.x()< (long)parent.x()+pad || (long)p.y()<(long)parent.y()+pad || (long)p.x()+p.w()>(long)parent.x()+parent.w()-pad || (long)p.y()+p.h()>(long)parent.y()+parent.h()-pad)throw new IllegalArgumentException("成员超出容器内边距范围");
            if(Boolean.TRUE.equals(parent.hidden()) && !Boolean.TRUE.equals(p.hidden()))throw new IllegalArgumentException("隐藏容器的成员必须隐藏");
            if(Boolean.TRUE.equals(parent.locked()) && !Boolean.TRUE.equals(p.locked()))throw new IllegalArgumentException("锁定容器的成员必须锁定");
        }
    }
}
