package com.dsh.petwhale.resource;

/**
 * 两套内置主题：原版鲸鱼娘 + AI 精致版鲸鱼娘。
 *
 * <p>每个主题对应一个 {@code pet.json} 清单文件，存放在 {@code resources/images/} 目录下。
 * 切主题时只需要更换 {@link #manifestResource} 指向的清单，精灵图本身在 {@code pet.json}
 * 的 {@code spritesheetPath} 字段里指定（同目录）。</p>
 *
 * <p>枚举值顺序与 {@link PetManifest#DEFAULT_FRAMES} 等契约保持稳定：
 * 新增主题请追加到尾部，不要插队。</p>
 */
public enum PetTheme {
    /** 原版鲸鱼娘（来自 dsh-pet 默认资源） */
    WHALE("/images/whale/pet.json"),
    /** 精致版鲸鱼娘（dsh-pet 内置的 AI 二次精修版） */
    WHALE_REFINED("/images/whale-refined/pet.json");

    /** classpath 资源路径，由 {@link PetManifestParser} 读取 */
    private final String manifestResource;

    PetTheme(String manifestResource) {
        this.manifestResource = manifestResource;
    }

    /** 取该主题对应的清单 classpath 路径。 */
    public String manifestResource() {
        return manifestResource;
    }
}