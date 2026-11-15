package com.newtermux.glass

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi

/**
 * AGSL RuntimeShader lens refraction for API 33+ (Android 13+).
 *
 * Uses a rounded-rectangle signed distance field so refraction concentrates
 * along the glass rim (liquid lens meniscus) while keeping central text crisp.
 */
object GlassShaders {

    private const val LENS_SHADER_SOURCE = """
        uniform shader content;
        uniform float2 resolution;
        uniform float cornerRadius;
        uniform float lensHeight;
        uniform float lensAmount;
        uniform int chromaticAberration;

        float sdRoundedBox(float2 p, float2 b, float r) {
            float2 q = abs(p) - b + r;
            return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r;
        }

        half4 main(float2 fragCoord) {
            float2 halfSize = resolution * 0.5;
            float2 p = fragCoord - halfSize;
            float r = min(cornerRadius, min(halfSize.x, halfSize.y));
            float dist = sdRoundedBox(p, halfSize, r);

            // Only refract inside the rounded shape near the perimeter band
            if (dist > 0.0 || lensHeight <= 0.5) {
                return content.eval(fragCoord);
            }

            float edgeDepth = clamp(-dist / lensHeight, 0.0, 1.0);
            float meniscus = (1.0 - edgeDepth) * (1.0 - edgeDepth);
            float2 normal = normalize(p + float2(0.0001, 0.0001));
            float2 offset = normal * (lensAmount * meniscus);

            float2 maxCoord = max(resolution - float2(1.0), float2(1.0));
            if (chromaticAberration != 0 && meniscus > 0.02) {
                float2 rCoord = clamp(fragCoord - offset * 1.08, float2(0.0), maxCoord);
                float2 gCoord = clamp(fragCoord - offset, float2(0.0), maxCoord);
                float2 bCoord = clamp(fragCoord - offset * 0.92, float2(0.0), maxCoord);
                half rCol = content.eval(rCoord).r;
                half gCol = content.eval(gCoord).g;
                half bCol = content.eval(bCoord).b;
                half aCol = content.eval(gCoord).a;
                return half4(rCol, gCol, bCol, aCol);
            } else {
                float2 sampleCoord = clamp(fragCoord - offset, float2(0.0), maxCoord);
                return content.eval(sampleCoord);
            }
        }
    """

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun createLensEffect(
        widthPx: Float,
        heightPx: Float,
        cornerRadiusPx: Float,
        lensHeightPx: Float,
        lensAmountPx: Float,
        chromaticAberration: Boolean = GlassTokens.CHROMATIC_ABERRATION,
    ): RenderEffect? {
        if (widthPx <= 1f || heightPx <= 1f || lensHeightPx <= 0.5f) return null
        val shader = RuntimeShader(LENS_SHADER_SOURCE).apply {
            setFloatUniform("resolution", widthPx, heightPx)
            setFloatUniform("cornerRadius", cornerRadiusPx)
            setFloatUniform("lensHeight", lensHeightPx)
            setFloatUniform("lensAmount", lensAmountPx)
            setIntUniform("chromaticAberration", if (chromaticAberration) 1 else 0)
        }
        return RenderEffect.createRuntimeShaderEffect(shader, "content")
    }
}
