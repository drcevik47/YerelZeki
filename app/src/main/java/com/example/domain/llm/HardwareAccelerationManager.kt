package com.example.domain.llm

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

enum class HardwareBackend(val displayName: String, val description: String) {
    AUTO("Otomatik", "Cihazın en uygun hızlandırıcısını seçer (NPU -> GPU -> CPU)"),
    GPU("GPU (Vulkan / OpenCL)", "Adreno / Mali grafik işlemcisi üzerinde paralel matris hesaplama"),
    NPU("NPU (Neural Processing Unit)", "Qualcomm Hexagon / MediaTek APU / NNAPI yapay zeka çipi"),
    CPU("CPU (ARM NEON)", "Çok çekirdekli işlemci ve vektör hızlandırma")
}

data class DeviceHardwareInfo(
    val socModel: String,
    val hardwareName: String,
    val totalRamMb: Long,
    val availableRamMb: Long,
    val supportsVulkan: Boolean,
    val vulkanVersion: String,
    val recommendedBackend: HardwareBackend,
    val npuSupported: Boolean
)

class HardwareAccelerationManager(private val context: Context) {

    fun getDeviceInfo(): DeviceHardwareInfo {
        val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        actManager?.getMemoryInfo(memInfo)

        val totalRam = memInfo.totalMem / (1024 * 1024)
        val availRam = memInfo.availMem / (1024 * 1024)

        val pm = context.packageManager
        val hasVulkan = pm.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_LEVEL)
        val vulkanVersion = if (pm.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_VERSION)) {
            "Vulkan 1.1+"
        } else if (hasVulkan) {
            "Vulkan 1.0"
        } else {
            "Desteklenmiyor"
        }

        val soc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Build.SOC_MODEL
        } else {
            Build.HARDWARE
        }

        // Check NPU / NNAPI availability
        val isNpuCapable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
                (soc.contains("snapdragon", ignoreCase = true) ||
                 soc.contains("dimensity", ignoreCase = true) ||
                 soc.contains("tensor", ignoreCase = true) ||
                 soc.contains("exynos", ignoreCase = true) ||
                 Build.HARDWARE.contains("qcom", ignoreCase = true) ||
                 Build.HARDWARE.contains("mt", ignoreCase = true))

        val recommended = when {
            isNpuCapable && totalRam >= 6000 -> HardwareBackend.NPU
            hasVulkan && totalRam >= 4000 -> HardwareBackend.GPU
            else -> HardwareBackend.GPU
        }

        return DeviceHardwareInfo(
            socModel = soc.ifBlank { Build.BOARD },
            hardwareName = "${Build.MANUFACTURER} ${Build.MODEL}",
            totalRamMb = totalRam,
            availableRamMb = availRam,
            supportsVulkan = hasVulkan,
            vulkanVersion = vulkanVersion,
            recommendedBackend = recommended,
            npuSupported = isNpuCapable
        )
    }
}
