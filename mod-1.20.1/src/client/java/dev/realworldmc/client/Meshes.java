package dev.realworldmc.client;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;

/** 1.20.1 has one shared tesselator builder: upload each mesh before beginning the next. */
final class Meshes {
    private Meshes() {}
    static BufferBuilder begin(VertexFormat.Mode mode, VertexFormat format) {
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(mode, format);
        return builder;
    }
}
