package shit.zen.render.shader;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import lombok.Getter;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL20;
import org.slf4j.Logger;
import shit.zen.render.shader.Matrix4Uniform;
import shit.zen.render.shader.ShaderFormats;
import shit.zen.render.shader.ShaderSource;

public class ShaderProgram {
    private static final Logger LOGGER = LogUtils.getLogger();
    @Getter
    private final int programId;
    @Getter
    private final Map<String, Integer> uniformCache = new HashMap<>();
    @Getter
    private final Matrix4Uniform modelViewUniform;
    @Getter
    private final Matrix4Uniform projectionUniform;
    @Getter
    private Matrix4f cachedModelView;
    @Getter
    private Matrix4f cachedProjection;
    private static int prevProgram;

    public ShaderProgram(String name) {
        this(name, "vertex", ShaderFormats.POSITION_UV, "ModelViewMat", "ProjMat");
    }

    public ShaderProgram(String fragmentName, String vertexName, Supplier<Map<Integer, String>> attributesSupplier) {
        this(fragmentName, vertexName, attributesSupplier, "ModelViewMat", "ProjMat");
    }

    public ShaderProgram(String name, Supplier<Map<Integer, String>> attributesSupplier) {
        this(name, name, attributesSupplier, "ModelViewMat", "ProjMat");
    }

    public ShaderProgram(String name, Supplier<Map<Integer, String>> attributesSupplier, String modelViewName, String projName) {
        this(name, name, attributesSupplier, modelViewName, projName);
    }

    public ShaderProgram(String fragmentName, String vertexName, Supplier<Map<Integer, String>> attributesSupplier, String modelViewName, String projName) {
        this.programId = GL20.glCreateProgram();
        int fragmentShader = ShaderProgram.compileShader(ShaderSource.getByFileName(fragmentName + ".fsh").getSource(), 35632);
        int vertexShader = ShaderProgram.compileShader(ShaderSource.getByFileName(vertexName + ".vsh").getSource(), 35633);
        GL20.glAttachShader(this.programId, fragmentShader);
        GL20.glAttachShader(this.programId, vertexShader);
        for (Map.Entry<Integer, String> entry : attributesSupplier.get().entrySet()) {
            GL20.glEnableVertexAttribArray(entry.getKey());
            GL20.glBindAttribLocation(this.programId, entry.getKey(), entry.getValue());
        }
        GL20.glLinkProgram(this.programId);
        if (GL20.glGetProgrami(this.programId, 35714) == 0) {
            LOGGER.error(GL20.glGetProgramInfoLog(this.programId, Short.MAX_VALUE));
            throw new IllegalStateException("Failed to link shader program!");
        }
        GL20.glDeleteShader(fragmentShader);
        GL20.glDeleteShader(vertexShader);
        this.modelViewUniform = new Matrix4Uniform(modelViewName).bindToProgram(this.programId);
        this.projectionUniform = new Matrix4Uniform(projName).bindToProgram(this.programId);
    }

    public void use() {
        prevProgram = GL20.glGetInteger(35725);
        GL20.glUseProgram(this.programId);
        this.setModelView(RenderSystem.getModelViewMatrix());
        this.setProjection(RenderSystem.getProjectionMatrix());
    }

    public void setModelView(Matrix4f modelView) {
        if (this.cachedModelView != modelView) {
            this.modelViewUniform.upload(modelView);
            this.cachedModelView = modelView;
        }
    }

    public void setProjection(Matrix4f projection) {
        if (this.cachedProjection != projection) {
            this.projectionUniform.upload(projection);
            this.cachedProjection = projection;
        }
    }

    public void stopUsing() {
        GL20.glUseProgram(prevProgram);
    }

    public int getUniformLocation(String uniformName) {
        if (!this.uniformCache.containsKey(uniformName)) {
            this.uniformCache.put(uniformName, GL20.glGetUniformLocation(this.programId, uniformName));
        }
        return this.uniformCache.get(uniformName);
    }

    private static int compileShader(String source, int type) {
        int shader = GL20.glCreateShader(type);
        Matcher matcher = ShaderFormats.IMPORT_PATTERN.matcher(source);
        while (matcher.find()) {
            boolean isPlainImport = matcher.group(2) == null;
            if (!isPlainImport) continue;
            String importName = matcher.group(3);
            String importSource = ShaderSource.getByFileName(importName).getSource();
            source = source.replaceAll(ShaderFormats.IMPORT_PATTERN.pattern(), importSource);
        }
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, 35713) == 0) {
            LOGGER.error(GL20.glGetShaderInfoLog(shader, Short.MAX_VALUE));
            throw new IllegalStateException(String.format("Failed to compile shader! (Type: %s)", new Object[]{type}));
        }
        return shader;
    }

    }