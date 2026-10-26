package com.z.liveconfig.vo;

import lombok.Data;

import java.util.List;

/** 权限点目录，给「新建自定义角色」渲染勾选框 */
@Data
public class PermGroupVO {

    private String group;
    private List<Item> items;

    @Data
    public static class Item {
        private String code;
        private String label;

        public static Item of(String code, String label) {
            Item i = new Item();
            i.setCode(code);
            i.setLabel(label);
            return i;
        }
    }
}
