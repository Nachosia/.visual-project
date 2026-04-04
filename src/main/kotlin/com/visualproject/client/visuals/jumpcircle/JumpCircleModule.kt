package com.visualproject.client.visuals.jumpcircle

import com.visualproject.client.ModuleStateStore
import com.visualproject.client.VisualThemeSettings
import com.visualproject.client.visuals.worldparticles.WorldParticlesModule

object JumpCircleModule {
    const val moduleId = "jump_circle"

    const val modeKey = "$moduleId:mode"
    const val showFirstPersonKey = "$moduleId:show_first_person"

    const val circleColorModeKey = "$moduleId:circle_color_mode"
    const val circleColorCountKey = "$moduleId:circle_color_count"
    const val circleColorAnimationKey = "$moduleId:circle_color_animation"
    const val circleTextureStyleKey = "$moduleId:circle_texture_style"
    const val circleAnimationTypeKey = "$moduleId:circle_animation_type"
    const val circleAppearDurationKey = "$moduleId:circle_appear_duration"
    const val circleExistDurationKey = "$moduleId:circle_exist_duration"
    const val circleDisappearDurationKey = "$moduleId:circle_disappear_duration"
    const val circleAppearInterpolationKey = "$moduleId:circle_appear_interpolation"
    const val circleDisappearInterpolationKey = "$moduleId:circle_disappear_interpolation"
    const val circleRotateSpeedKey = "$moduleId:circle_rotate_speed"
    const val circleScaleKey = "$moduleId:circle_scale"
    const val circleRadiusKey = "$moduleId:circle_radius"
    const val circleSpeedKey = "$moduleId:circle_speed"
    const val circleClientColorKey = "$moduleId:circle_client_color"
    const val circleCustomColorKey = "$moduleId:circle_custom_color"
    const val circleCustomColor2Key = "$moduleId:circle_custom_color_2"
    const val circleCustomColor3Key = "$moduleId:circle_custom_color_3"
    const val circleCustomColor4Key = "$moduleId:circle_custom_color_4"

    const val particleTypeKey = "$moduleId:particle_type"
    const val particlePhysicsKey = "$moduleId:particle_physics"
    const val particleCountKey = "$moduleId:particle_count"
    const val particleSizeKey = "$moduleId:particle_size"
    const val particleLifetimeKey = "$moduleId:particle_lifetime"
    const val particleSpreadKey = "$moduleId:particle_spread"
    const val particleClientColorKey = "$moduleId:particle_client_color"
    const val particleCustomColorKey = "$moduleId:particle_custom_color"
    const val particleCustomFileKey = "$moduleId:particle_custom_file"

    const val waveRadiusKey = "$moduleId:wave_radius"
    const val waveSpeedKey = "$moduleId:wave_speed"
    const val waveThicknessKey = "$moduleId:wave_thickness"
    const val waveOutlineKey = "$moduleId:wave_outline"
    const val waveLineThicknessKey = "$moduleId:wave_line_thickness"
    const val waveFillKey = "$moduleId:wave_fill"
    const val waveFillTypeKey = "$moduleId:wave_fill_type"
    const val waveFillAlphaKey = "$moduleId:wave_fill_alpha"
    const val waveClientColorKey = "$moduleId:wave_client_color"
    const val waveCustomColorKey = "$moduleId:wave_custom_color"
    const val waveFillClientColorKey = "$moduleId:wave_fill_client_color"
    const val waveFillCustomColorKey = "$moduleId:wave_fill_custom_color"
    const val waveShaderTypeKey = "$moduleId:wave_shader_type"
    const val waveShaderSpeedKey = "$moduleId:wave_shader_speed"
    const val waveShaderAlphaKey = "$moduleId:wave_shader_alpha"
    const val wavePlasmaSecondaryClientColorKey = "$moduleId:wave_plasma_secondary_client_color"
    const val wavePlasmaSecondaryCustomColorKey = "$moduleId:wave_plasma_secondary_custom_color"

    enum class Mode(val id: String, val label: String) {
        CIRCLE_ONLY("circle_only", "Только круг"),
        PARTICLES_ONLY("particles_only", "Только частицы"),
        CIRCLE_AND_PARTICLES("circle_particles", "Круг + частицы"),
        BLOCK_WAVE("block_wave", "Блоковая волна");

        companion object {
            fun fromId(raw: String): Mode {
                return entries.firstOrNull { it.id.equals(raw.trim(), ignoreCase = true) } ?: CIRCLE_ONLY
            }
        }
    }

    enum class ParticlePhysics(val id: String, val label: String) {
        REALISTIC("realistic", "Реалистичная"),
        NO_COLLISION("no_collision", "Без коллизий"),
        NO_PHYSICS("no_physics", "Без физики"),
        ATTRACTION("attraction", "Притяжение");

        companion object {
            fun fromId(raw: String): ParticlePhysics {
                return entries.firstOrNull { it.id.equals(raw.trim(), ignoreCase = true) } ?: REALISTIC
            }
        }
    }

    enum class FillType(val id: String, val label: String) {
        COLOR("color", "Цвет"),
        SHADER_MASK("shader_mask", "Шейдер-маска");

        companion object {
            fun fromId(raw: String): FillType {
                val normalized = raw.trim()
                return when {
                    normalized.equals("solid", ignoreCase = true) -> COLOR
                    normalized.equals("shader", ignoreCase = true) -> SHADER_MASK
                    else -> entries.firstOrNull { it.id.equals(normalized, ignoreCase = true) } ?: COLOR
                }
            }
        }
    }

    enum class CircleColorMode(val id: String, val label: String) {
        SYNC("sync", "Синхрон"),
        CUSTOM("custom", "Кастом");

        companion object {
            fun fromId(raw: String): CircleColorMode {
                return entries.firstOrNull { it.id.equals(raw.trim(), ignoreCase = true) } ?: SYNC
            }
        }
    }

    enum class CircleColorCount(val id: String, val label: String, val count: Int) {
        SOLO("solo", "Один", 1),
        DUO("duo", "Два", 2),
        TRIPLE("triple", "Три", 3),
        QUARTET("quartet", "Четыре", 4);

        companion object {
            fun fromId(raw: String): CircleColorCount {
                return entries.firstOrNull { it.id.equals(raw.trim(), ignoreCase = true) } ?: SOLO
            }
        }
    }

    enum class CircleColorAnimation(val id: String, val label: String) {
        WAVE("wave", "Волна"),
        VERTEXES("vertexes", "Вершины");

        companion object {
            fun fromId(raw: String): CircleColorAnimation {
                return entries.firstOrNull { it.id.equals(raw.trim(), ignoreCase = true) } ?: WAVE
            }
        }
    }

    enum class CircleTextureStyle(val id: String, val label: String) {
        DEFAULT("default", "Default"),
        BOLD("bold", "Bold"),
        PORTAL("portal", "Portal");

        companion object {
            fun fromId(raw: String): CircleTextureStyle {
                val normalized = raw.trim()
                if (normalized.equals("soup", ignoreCase = true)) {
                    return DEFAULT
                }
                return entries.firstOrNull { it.id.equals(normalized, ignoreCase = true) } ?: DEFAULT
            }
        }
    }

    enum class CircleAnimationType(val id: String, val label: String) {
        FADE("fade", "Fade"),
        SCALE("scale", "Scale"),
        BOTH("both", "Both");

        companion object {
            fun fromId(raw: String): CircleAnimationType {
                return entries.firstOrNull { it.id.equals(raw.trim(), ignoreCase = true) } ?: BOTH
            }
        }
    }

    enum class ShaderType(val id: String, val label: String) {
        NEBULA("nebula", "Небула"),
        STARS("stars", "Звёзды"),
        WEB("web", "Паутина");

        companion object {
            fun fromId(raw: String): ShaderType {
                return entries.firstOrNull { it.id.equals(raw.trim(), ignoreCase = true) } ?: NEBULA
            }
        }
    }

    data class ParticleTypeOption(
        val type: WorldParticlesModule.ParticleType,
        val label: String,
    )

    val particleTypeOptions = listOf(
        ParticleTypeOption(WorldParticlesModule.ParticleType.SPARK, "Искра"),
        ParticleTypeOption(WorldParticlesModule.ParticleType.SUN, "Солнце"),
        ParticleTypeOption(WorldParticlesModule.ParticleType.SNOWFLAKE, "Снежинка"),
        ParticleTypeOption(WorldParticlesModule.ParticleType.PAYMENTS, "Платёж"),
        ParticleTypeOption(WorldParticlesModule.ParticleType.DOLLAR, "Доллар"),
        ParticleTypeOption(WorldParticlesModule.ParticleType.HEART, "Сердце"),
        ParticleTypeOption(WorldParticlesModule.ParticleType.WATER_DROP, "Капля"),
        ParticleTypeOption(WorldParticlesModule.ParticleType.STAR, "Звезда"),
        ParticleTypeOption(WorldParticlesModule.ParticleType.MOON, "Луна"),
        ParticleTypeOption(WorldParticlesModule.ParticleType.BOLT, "Молния"),
        ParticleTypeOption(WorldParticlesModule.ParticleType.NEARBY, "Рядом"),
        ParticleTypeOption(WorldParticlesModule.ParticleType.BLINK, "Blink"),
        ParticleTypeOption(WorldParticlesModule.ParticleType.CORON, "Coron"),
        ParticleTypeOption(WorldParticlesModule.ParticleType.FIREFLY, "Firefly"),
        ParticleTypeOption(WorldParticlesModule.ParticleType.FLAME, "Flame"),
        ParticleTypeOption(WorldParticlesModule.ParticleType.GEOMETRIC, "Geometric"),
        ParticleTypeOption(WorldParticlesModule.ParticleType.VIRUS, "Virus"),
        ParticleTypeOption(WorldParticlesModule.ParticleType.AMONGUS, "Among Us"),
        ParticleTypeOption(WorldParticlesModule.ParticleType.BLOOM, "Bloom"),
        ParticleTypeOption(WorldParticlesModule.ParticleType.GLYPH, "Glyph"),
        ParticleTypeOption(WorldParticlesModule.ParticleType.GLYPH_ALT, "Glyph Alt"),
        ParticleTypeOption(WorldParticlesModule.ParticleType.CUSTOM, "Кастом"),
    )

    private const val defaultCircleRadius = 1.0f
    private const val defaultCircleSpeed = 2.0f
    private const val defaultCircleAppearDuration = 1.0f
    private const val defaultCircleExistDuration = 1.5f
    private const val defaultCircleDisappearDuration = 1.0f
    private const val defaultCircleRotateSpeed = 2.0f
    private const val defaultCircleScale = 1.0f
    private const val defaultParticleCount = 4f
    private const val defaultParticleSize = 0.30f
    private const val defaultParticleLifetime = 20f
    private const val defaultParticleSpread = 0.30f
    private const val defaultWaveRadius = 35f
    private const val defaultWaveSpeed = 25.0f
    private const val defaultWaveThickness = 3.0f
    private const val defaultWaveLineThickness = 1.0f
    private const val defaultWaveFillAlpha = 0.30f
    private const val defaultWaveShaderSpeed = 1.0f
    private const val defaultWaveShaderAlpha = 0.30f
    private const val defaultWhiteHex = "#FFFFFF"

    fun initialize() {
        ModuleStateStore.ensureModule(moduleId, defaultEnabled = false)
        ModuleStateStore.ensureTextSetting(modeKey, Mode.CIRCLE_ONLY.id)
        ModuleStateStore.ensureSetting(showFirstPersonKey, defaultValue = true)

        ModuleStateStore.ensureTextSetting(circleColorModeKey, CircleColorMode.SYNC.id)
        ModuleStateStore.ensureTextSetting(circleColorCountKey, CircleColorCount.SOLO.id)
        ModuleStateStore.ensureTextSetting(circleColorAnimationKey, CircleColorAnimation.WAVE.id)
        ModuleStateStore.ensureTextSetting(circleTextureStyleKey, CircleTextureStyle.DEFAULT.id)
        ModuleStateStore.ensureTextSetting(circleAnimationTypeKey, CircleAnimationType.BOTH.id)
        ModuleStateStore.ensureNumberSetting(circleAppearDurationKey, defaultCircleAppearDuration)
        ModuleStateStore.ensureNumberSetting(circleExistDurationKey, defaultCircleExistDuration)
        ModuleStateStore.ensureNumberSetting(circleDisappearDurationKey, defaultCircleDisappearDuration)
        ModuleStateStore.ensureTextSetting(circleAppearInterpolationKey, CircleInterpolationPreset.BOUNCE.id)
        ModuleStateStore.ensureTextSetting(circleDisappearInterpolationKey, CircleInterpolationPreset.SMOOTH.id)
        ModuleStateStore.ensureNumberSetting(circleRotateSpeedKey, defaultCircleRotateSpeed)
        ModuleStateStore.ensureNumberSetting(circleScaleKey, defaultCircleScale)
        ModuleStateStore.ensureNumberSetting(circleRadiusKey, defaultCircleRadius)
        ModuleStateStore.ensureNumberSetting(circleSpeedKey, defaultCircleSpeed)
        ModuleStateStore.ensureSetting(circleClientColorKey, defaultValue = true)
        ModuleStateStore.ensureTextSetting(circleCustomColorKey, defaultWhiteHex)
        ModuleStateStore.ensureTextSetting(circleCustomColor2Key, "#3574F0")
        ModuleStateStore.ensureTextSetting(circleCustomColor3Key, "#9B59B6")
        ModuleStateStore.ensureTextSetting(circleCustomColor4Key, "#F9C12B")
        if (!ModuleStateStore.isSettingEnabled(circleClientColorKey) &&
            ModuleStateStore.getTextSetting(circleColorModeKey, CircleColorMode.SYNC.id).equals(CircleColorMode.SYNC.id, ignoreCase = true)
        ) {
            ModuleStateStore.setTextSetting(circleColorModeKey, CircleColorMode.CUSTOM.id)
        }

        ModuleStateStore.ensureTextSetting(particleTypeKey, WorldParticlesModule.ParticleType.HEART.id)
        ModuleStateStore.ensureTextSetting(particlePhysicsKey, ParticlePhysics.REALISTIC.id)
        ModuleStateStore.ensureNumberSetting(particleCountKey, defaultParticleCount)
        ModuleStateStore.ensureNumberSetting(particleSizeKey, defaultParticleSize)
        ModuleStateStore.ensureNumberSetting(particleLifetimeKey, defaultParticleLifetime)
        ModuleStateStore.ensureNumberSetting(particleSpreadKey, defaultParticleSpread)
        ModuleStateStore.ensureSetting(particleClientColorKey, defaultValue = true)
        ModuleStateStore.ensureTextSetting(particleCustomColorKey, defaultWhiteHex)
        ModuleStateStore.ensureTextSetting(particleCustomFileKey, "")

        ModuleStateStore.ensureNumberSetting(waveRadiusKey, defaultWaveRadius)
        ModuleStateStore.ensureNumberSetting(waveSpeedKey, defaultWaveSpeed)
        ModuleStateStore.ensureNumberSetting(waveThicknessKey, defaultWaveThickness)
        ModuleStateStore.ensureSetting(waveOutlineKey, defaultValue = true)
        ModuleStateStore.ensureNumberSetting(waveLineThicknessKey, defaultWaveLineThickness)
        ModuleStateStore.ensureSetting(waveFillKey, defaultValue = true)
        ModuleStateStore.ensureTextSetting(waveFillTypeKey, FillType.COLOR.id)
        ModuleStateStore.ensureNumberSetting(waveFillAlphaKey, defaultWaveFillAlpha)
        ModuleStateStore.ensureSetting(waveClientColorKey, defaultValue = true)
        ModuleStateStore.ensureTextSetting(waveCustomColorKey, defaultWhiteHex)
        ModuleStateStore.ensureSetting(waveFillClientColorKey, defaultValue = true)
        ModuleStateStore.ensureTextSetting(waveFillCustomColorKey, defaultWhiteHex)
        ModuleStateStore.ensureTextSetting(waveShaderTypeKey, ShaderType.NEBULA.id)
        ModuleStateStore.ensureNumberSetting(waveShaderSpeedKey, defaultWaveShaderSpeed)
        ModuleStateStore.ensureNumberSetting(waveShaderAlphaKey, defaultWaveShaderAlpha)
        ModuleStateStore.ensureSetting(wavePlasmaSecondaryClientColorKey, defaultValue = false)
        ModuleStateStore.ensureTextSetting(wavePlasmaSecondaryCustomColorKey, defaultWhiteHex)
        JumpCircleRenderer.initialize()
    }

    fun isActive(): Boolean = ModuleStateStore.isEnabled(moduleId)

    fun mode(): Mode = Mode.fromId(ModuleStateStore.getTextSetting(modeKey, Mode.CIRCLE_ONLY.id))

    fun showFirstPerson(): Boolean = ModuleStateStore.isSettingEnabled(showFirstPersonKey)

    fun circleColorMode(): CircleColorMode =
        CircleColorMode.fromId(ModuleStateStore.getTextSetting(circleColorModeKey, CircleColorMode.SYNC.id))

    fun circleColorCount(): CircleColorCount =
        CircleColorCount.fromId(ModuleStateStore.getTextSetting(circleColorCountKey, CircleColorCount.SOLO.id))

    fun circleColorAnimation(): CircleColorAnimation =
        CircleColorAnimation.fromId(ModuleStateStore.getTextSetting(circleColorAnimationKey, CircleColorAnimation.WAVE.id))

    fun circleTextureStyle(): CircleTextureStyle =
        CircleTextureStyle.fromId(ModuleStateStore.getTextSetting(circleTextureStyleKey, CircleTextureStyle.DEFAULT.id))

    fun circleAnimationType(): CircleAnimationType =
        CircleAnimationType.fromId(ModuleStateStore.getTextSetting(circleAnimationTypeKey, CircleAnimationType.BOTH.id))

    fun circleAppearDuration(): Float =
        ModuleStateStore.getNumberSetting(circleAppearDurationKey, defaultCircleAppearDuration).coerceIn(0.10f, 3.0f)

    fun circleExistDuration(): Float =
        ModuleStateStore.getNumberSetting(circleExistDurationKey, defaultCircleExistDuration).coerceIn(0.10f, 3.0f)

    fun circleDisappearDuration(): Float =
        ModuleStateStore.getNumberSetting(circleDisappearDurationKey, defaultCircleDisappearDuration).coerceIn(0.10f, 3.0f)

    fun circleAppearInterpolation(): CircleInterpolationPreset =
        CircleInterpolationPreset.fromId(ModuleStateStore.getTextSetting(circleAppearInterpolationKey, CircleInterpolationPreset.BOUNCE.id))

    fun circleDisappearInterpolation(): CircleInterpolationPreset =
        CircleInterpolationPreset.fromId(ModuleStateStore.getTextSetting(circleDisappearInterpolationKey, CircleInterpolationPreset.SMOOTH.id))

    fun circleRotateSpeed(): Float =
        ModuleStateStore.getNumberSetting(circleRotateSpeedKey, defaultCircleRotateSpeed).coerceIn(0.50f, 5.0f)

    fun circleScale(): Float =
        ModuleStateStore.getNumberSetting(circleScaleKey, defaultCircleScale).coerceIn(0.50f, 3.0f)

    fun circleAnimation(): ThreeStageAnimation {
        return ThreeStageAnimation(
            appearDuration = circleAppearDuration(),
            existDuration = circleExistDuration(),
            disappearDuration = circleDisappearDuration(),
            appearInterpolation = circleAppearInterpolation(),
            disappearInterpolation = circleDisappearInterpolation(),
        )
    }

    fun circleRadius(): Float = ModuleStateStore.getNumberSetting(circleRadiusKey, defaultCircleRadius).coerceIn(0.20f, 12.0f)

    fun circleSpeed(): Float = ModuleStateStore.getNumberSetting(circleSpeedKey, defaultCircleSpeed).coerceIn(0.10f, 20.0f)

    fun circleColor(): Int {
        return if (circleColorMode() == CircleColorMode.SYNC) {
            VisualThemeSettings.accentStrong()
        } else {
            circlePalette().firstOrNull() ?: parseColor(ModuleStateStore.getTextSetting(circleCustomColorKey, defaultWhiteHex), 0xFFFFFFFF.toInt())
        }
    }

    fun circlePalette(): IntArray {
        val palette = intArrayOf(
            parseColor(ModuleStateStore.getTextSetting(circleCustomColorKey, defaultWhiteHex), 0xFFFFFFFF.toInt()),
            parseColor(ModuleStateStore.getTextSetting(circleCustomColor2Key, "#3574F0"), 0xFF3574F0.toInt()),
            parseColor(ModuleStateStore.getTextSetting(circleCustomColor3Key, "#9B59B6"), 0xFF9B59B6.toInt()),
            parseColor(ModuleStateStore.getTextSetting(circleCustomColor4Key, "#F9C12B"), 0xFFF9C12B.toInt()),
        )
        return palette.copyOf(circleColorCount().count)
    }

    fun particleType(): WorldParticlesModule.ParticleType {
        val raw = ModuleStateStore.getTextSetting(particleTypeKey, WorldParticlesModule.ParticleType.HEART.id)
        return particleTypeOptions.firstOrNull { it.type.id.equals(raw.trim(), ignoreCase = true) }?.type
            ?: WorldParticlesModule.ParticleType.HEART
    }

    fun particlePhysics(): ParticlePhysics = ParticlePhysics.fromId(ModuleStateStore.getTextSetting(particlePhysicsKey, ParticlePhysics.REALISTIC.id))

    fun particleCount(): Int = ModuleStateStore.getNumberSetting(particleCountKey, defaultParticleCount).coerceIn(1f, 40f).toInt()

    fun particleSize(): Float = ModuleStateStore.getNumberSetting(particleSizeKey, defaultParticleSize).coerceIn(0.05f, 1.50f)

    fun particleLifetime(): Int = ModuleStateStore.getNumberSetting(particleLifetimeKey, defaultParticleLifetime).coerceIn(4f, 240f).toInt()

    fun particleSpread(): Float = ModuleStateStore.getNumberSetting(particleSpreadKey, defaultParticleSpread).coerceIn(0.01f, 2.50f)

    fun particleColor(): Int = resolveColor(particleClientColorKey, particleCustomColorKey, 0xFFFFFFFF.toInt())

    fun particleCustomFile(): String = ModuleStateStore.getTextSetting(particleCustomFileKey, "").trim()

    fun waveRadius(): Float = ModuleStateStore.getNumberSetting(waveRadiusKey, defaultWaveRadius).coerceIn(2f, 96f)

    fun waveSpeed(): Float = ModuleStateStore.getNumberSetting(waveSpeedKey, defaultWaveSpeed).coerceIn(0.5f, 60f)

    fun waveThickness(): Float = ModuleStateStore.getNumberSetting(waveThicknessKey, defaultWaveThickness).coerceIn(0.20f, 8.0f)

    fun waveOutline(): Boolean = ModuleStateStore.isSettingEnabled(waveOutlineKey)

    fun waveLineThickness(): Float = ModuleStateStore.getNumberSetting(waveLineThicknessKey, defaultWaveLineThickness).coerceIn(0.50f, 5.0f)

    fun waveFill(): Boolean = ModuleStateStore.isSettingEnabled(waveFillKey)

    fun waveFillType(): FillType = FillType.fromId(ModuleStateStore.getTextSetting(waveFillTypeKey, FillType.COLOR.id))

    fun waveFillAlpha(): Float = ModuleStateStore.getNumberSetting(waveFillAlphaKey, defaultWaveFillAlpha).coerceIn(0.02f, 1.0f)

    fun waveColor(): Int = resolveColor(waveClientColorKey, waveCustomColorKey, 0xFFFFFFFF.toInt())

    fun waveFillColor(): Int = resolveColor(waveFillClientColorKey, waveFillCustomColorKey, 0xFFFFFFFF.toInt())

    fun waveShaderType(): ShaderType = ShaderType.fromId(ModuleStateStore.getTextSetting(waveShaderTypeKey, ShaderType.NEBULA.id))

    fun waveShaderSpeed(): Float = ModuleStateStore.getNumberSetting(waveShaderSpeedKey, defaultWaveShaderSpeed).coerceIn(0.05f, 6.0f)

    fun waveShaderAlpha(): Float = ModuleStateStore.getNumberSetting(waveShaderAlphaKey, defaultWaveShaderAlpha).coerceIn(0.02f, 1.0f)

    fun wavePlasmaSecondaryColor(): Int = derivePlasmaSecondaryColor(waveFillColor())

    private fun resolveColor(clientColorKey: String, customColorKey: String, fallback: Int): Int {
        return if (ModuleStateStore.isSettingEnabled(clientColorKey)) {
            VisualThemeSettings.accentStrong()
        } else {
            parseColor(ModuleStateStore.getTextSetting(customColorKey, defaultWhiteHex), fallback)
        }
    }

    private fun derivePlasmaSecondaryColor(primaryColor: Int): Int {
        val red = ((primaryColor ushr 16) and 0xFF) / 255f
        val green = ((primaryColor ushr 8) and 0xFF) / 255f
        val blue = (primaryColor and 0xFF) / 255f

        val shiftedRed = ((red * 0.55f) + (blue * 0.45f)).coerceIn(0f, 1f)
        val shiftedGreen = ((green * 0.55f) + (red * 0.45f)).coerceIn(0f, 1f)
        val shiftedBlue = ((blue * 0.55f) + (green * 0.45f)).coerceIn(0f, 1f)

        val finalRed = (shiftedRed + ((1f - shiftedRed) * 0.32f)).coerceIn(0f, 1f)
        val finalGreen = (shiftedGreen + ((1f - shiftedGreen) * 0.32f)).coerceIn(0f, 1f)
        val finalBlue = (shiftedBlue + ((1f - shiftedBlue) * 0.32f)).coerceIn(0f, 1f)

        return (0xFF shl 24) or
            ((finalRed * 255f).toInt().coerceIn(0, 255) shl 16) or
            ((finalGreen * 255f).toInt().coerceIn(0, 255) shl 8) or
            (finalBlue * 255f).toInt().coerceIn(0, 255)
    }

    private fun parseColor(raw: String, fallback: Int): Int {
        val compact = raw.trim().removePrefix("#")
        val parsed = when (compact.length) {
            6 -> compact.toLongOrNull(16)?.toInt()?.let { 0xFF000000.toInt() or it }
            8 -> compact.toLongOrNull(16)?.toInt()
            else -> null
        }
        return parsed ?: fallback
    }
}
