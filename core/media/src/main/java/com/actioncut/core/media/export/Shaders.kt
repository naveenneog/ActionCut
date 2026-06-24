package com.actioncut.core.media.export

import com.actioncut.core.model.VisualEffectType

/**
 * GLSL ES 1.00 fragment shaders for ActionCut's stylized effects, paired to the
 * [ShaderEffect] uniform contract. Each returns final colour in `gl_FragColor`.
 *
 * Kept as inline strings (no asset files) so the render path is fully self-contained.
 */
internal object Shaders {

    /** Common header shared by every fragment shader (uniforms + a cheap hash noise). */
    private const val HEADER = """
        #version 100
        precision highp float;
        uniform sampler2D uTexSampler;
        uniform float uIntensity;
        uniform float uTime;
        uniform vec2 uResolution;
        varying vec2 vTexSamplingCoord;
        float rand(vec2 c) { return fract(sin(dot(c, vec2(12.9898, 78.233))) * 43758.5453); }
    """

    /** Returns the fragment shader for [type], or null if it is not a stylized shader effect. */
    fun fragmentFor(type: VisualEffectType): String? = when (type) {
        VisualEffectType.GLITCH -> GLITCH
        VisualEffectType.RGB_SPLIT -> RGB_SPLIT
        VisualEffectType.SHAKE -> SHAKE
        VisualEffectType.ZOOM_PULSE -> ZOOM_PULSE
        VisualEffectType.FILM_GRAIN -> FILM_GRAIN
        VisualEffectType.LIGHT_LEAK -> LIGHT_LEAK
        VisualEffectType.VHS -> VHS
        VisualEffectType.PIXELATE -> PIXELATE
        // Blur variants use stock Media3 GaussianBlur instead of a custom shader.
        VisualEffectType.GAUSSIAN_BLUR,
        VisualEffectType.RADIAL_BLUR,
        VisualEffectType.BOKEH,
        -> null
    }

    private val RGB_SPLIT = HEADER + """
        void main() {
          float a = uIntensity * 0.012;
          vec2 uv = vTexSamplingCoord;
          float r = texture2D(uTexSampler, uv + vec2(a, 0.0)).r;
          float g = texture2D(uTexSampler, uv).g;
          float b = texture2D(uTexSampler, uv - vec2(a, 0.0)).b;
          gl_FragColor = vec4(r, g, b, 1.0);
        }
    """

    private val GLITCH = HEADER + """
        void main() {
          vec2 uv = vTexSamplingCoord;
          float blockH = 0.06;
          float line = floor(uv.y / blockH);
          float n = rand(vec2(line, floor(uTime * 12.0)));
          float shift = (n - 0.5) * uIntensity * 0.12 * step(0.72, n);
          uv.x = fract(uv.x + shift);
          float a = uIntensity * 0.014;
          float r = texture2D(uTexSampler, uv + vec2(a, 0.0)).r;
          float g = texture2D(uTexSampler, uv).g;
          float b = texture2D(uTexSampler, uv - vec2(a, 0.0)).b;
          gl_FragColor = vec4(r, g, b, 1.0);
        }
    """

    private val SHAKE = HEADER + """
        void main() {
          float t = floor(uTime * 18.0);
          vec2 j = vec2(rand(vec2(t, 0.0)) - 0.5, rand(vec2(0.0, t)) - 0.5) * uIntensity * 0.04;
          gl_FragColor = texture2D(uTexSampler, vTexSamplingCoord + j);
        }
    """

    private val ZOOM_PULSE = HEADER + """
        void main() {
          float s = 1.0 - 0.10 * uIntensity * (0.5 + 0.5 * sin(uTime * 3.14159));
          vec2 uv = (vTexSamplingCoord - 0.5) * s + 0.5;
          gl_FragColor = texture2D(uTexSampler, uv);
        }
    """

    private val FILM_GRAIN = HEADER + """
        void main() {
          vec4 col = texture2D(uTexSampler, vTexSamplingCoord);
          float g = rand(vTexSamplingCoord * uResolution + fract(uTime)) - 0.5;
          col.rgb += g * uIntensity * 0.25;
          gl_FragColor = vec4(clamp(col.rgb, 0.0, 1.0), col.a);
        }
    """

    private val LIGHT_LEAK = HEADER + """
        void main() {
          vec4 col = texture2D(uTexSampler, vTexSamplingCoord);
          float pos = 0.5 + 0.5 * sin(uTime * 0.7);
          float d = smoothstep(0.0, 0.6, 1.0 - abs(vTexSamplingCoord.x - pos));
          vec3 leak = vec3(1.0, 0.55, 0.2) * d * uIntensity * 0.6;
          vec3 outc = 1.0 - (1.0 - col.rgb) * (1.0 - leak); // screen blend
          gl_FragColor = vec4(outc, col.a);
        }
    """

    private val VHS = HEADER + """
        void main() {
          vec2 uv = vTexSamplingCoord;
          float wobble = sin(uv.y * 80.0 + uTime * 5.0) * 0.0015 * uIntensity;
          uv.x += wobble;
          float a = uIntensity * 0.008;
          float r = texture2D(uTexSampler, uv + vec2(a, 0.0)).r;
          float g = texture2D(uTexSampler, uv).g;
          float b = texture2D(uTexSampler, uv - vec2(a, 0.0)).b;
          vec3 col = vec3(r, g, b);
          float scan = 0.9 + 0.1 * sin(uv.y * uResolution.y * 1.5);
          col *= mix(1.0, scan, uIntensity);
          col += rand(uv * uResolution + fract(uTime)) * 0.10 * uIntensity;
          gl_FragColor = vec4(clamp(col, 0.0, 1.0), 1.0);
        }
    """

    private val PIXELATE = HEADER + """
        void main() {
          float px = mix(1.0, 40.0, clamp(uIntensity, 0.0, 1.0));
          vec2 grid = max(uResolution / px, vec2(1.0));
          vec2 uv = (floor(vTexSamplingCoord * grid) + 0.5) / grid;
          gl_FragColor = texture2D(uTexSampler, uv);
        }
    """
}
