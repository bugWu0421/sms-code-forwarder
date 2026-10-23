package com.wj.smsforwarder

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

/**
 * 国产 ROM 后台保活适配：按厂商直达对应的"自启动 / 后台运行"设置页。
 *
 * 各厂商均无公开 API，只能尝试跳转其系统管家内的已知页面；
 * 页面路径随系统版本变动，因此逐个尝试，全部失败则退到本应用详情页，
 * 用户从详情页手动进入"电池/自启动"相关选项，任何 ROM 都可达。
 */
object KeepAliveHelper {

    enum class Brand {
        XIAOMI,      // 小米 / 红米（MIUI / HyperOS / 澎湃）
        HUAWEI,      // 华为（含 2021 前的荣耀）
        HONOR,       // 荣耀（独立后 MagicOS）
        OPPO,        // OPPO / 一加 / 真我（ColorOS）
        VIVO,        // vivo / iQOO（OriginOS / Funtouch）
        SAMSUNG,     // 三星 One UI
        MEIZU,       // 魅族 Flyme
        OTHER
    }

    fun brand(): Brand {
        val m = (Build.MANUFACTURER ?: "").lowercase()
        val b = (Build.BRAND ?: "").lowercase()
        val both = "$m $b"
        return when {
            "xiaomi" in both || "redmi" in both -> Brand.XIAOMI
            "huawei" in both -> Brand.HUAWEI
            "honor" in both || "hihonor" in both -> Brand.HONOR
            "oppo" in both || "oneplus" in both || "realme" in both -> Brand.OPPO
            "vivo" in both || "iqoo" in both -> Brand.VIVO
            "samsung" in both -> Brand.SAMSUNG
            "meizu" in both -> Brand.MEIZU
            else -> Brand.OTHER
        }
    }

    /** 按钮文案（按本机品牌给出，OTHER 显示通用文案） */
    fun buttonLabel(): String = when (brand()) {
        Brand.XIAOMI -> "③ 允许自启动（小米/红米必做，否则息屏后不转发）"
        Brand.HUAWEI, Brand.HONOR -> "③ 允许自启动（华为/荣耀必做，否则息屏后不转发）"
        Brand.OPPO -> "③ 允许自启动（OPPO/一加/真我必做，否则息屏后不转发）"
        Brand.VIVO -> "③ 允许自启动（vivo/iQOO 必做，否则息屏后不转发）"
        Brand.SAMSUNG -> "③ 后台运行设置（三星：解除深度睡眠限制）"
        Brand.MEIZU -> "③ 允许自启动（魅族必做，否则息屏后不转发）"
        Brand.OTHER -> "③ 允许自启动/后台运行（国产系统必做）"
    }

    /**
     * 跳转到本机品牌对应的自启动/后台管理页。
     * @return 给用户看的操作指引文案（Toast）。
     */
    fun open(context: Context): String {
        val targets: List<ComponentName> = when (brand()) {
            Brand.XIAOMI -> listOf(
                cn("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
                cn("com.miui.securitycenter", "com.miui.permcenter.permissions.PermissionsAppListActivity"),
                cn("com.xiaomi.misettings", "com.xiaomi.misettings.applications.RunangleAutostart")
            )
            Brand.HUAWEI -> listOf(
                cn("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
                cn("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupAppListActivity"),
                cn("com.huawei.systemmanager", "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity"),
                cn("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity")
            )
            Brand.HONOR -> listOf(
                cn("com.hihonor.systemmanager", "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
                cn("com.hihonor.systemmanager", "com.hihonor.systemmanager.appcontrol.activity.StartupAppControlActivity"),
                // 老款荣耀仍是华为系统管家
                cn("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity")
            )
            Brand.OPPO -> listOf(
                cn("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
                cn("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"),
                cn("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"),
                cn("com.oneplus.security", "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"),
                cn("com.oplus.battery", "com.oplus.powermanager.frozenapplication.FrozenApplicationListActivity")
            )
            Brand.VIVO -> listOf(
                cn("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
                cn("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.PurviewTabActivity"),
                cn("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager"),
                cn("com.iqoo.secure", "com.iqoo.secure.safeguard.PurviewTabActivity")
            )
            Brand.SAMSUNG -> listOf(
                cn("com.samsung.android.lool", "com.samsung.android.sm.battery.ui.BatteryActivity"),
                cn("com.samsung.android.sm", "com.samsung.android.sm.battery.ui.BatteryActivity")
            )
            Brand.MEIZU -> listOf(
                cn("com.meizu.safe", "com.meizu.safe.security.SHOW_APPSEC"),
                cn("com.meizu.flymeautorun", "com.meizu.flymeautorun.permission.AutoRunPermissionListActivity")
            )
            Brand.OTHER -> emptyList()
        }

        for (t in targets) {
            try {
                context.startActivity(Intent().setComponent(t).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return guideText()
            } catch (_: Exception) { }
        }

        // 通用兜底：本应用详情页，任何 ROM 都能进，从里面手动找"电池/自启动"
        return try {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            "未找到「${brandName()}」专属设置页，已打开应用详情页：请在其中找到「自启动/电池」，允许后台运行"
        } catch (_: Exception) {
            "请到 系统设置 → 应用 → ${brandName()}对应选项 中手动允许自启动"
        }
    }

    /** 跳转成功后的操作指引 */
    private fun guideText(): String = when (brand()) {
        Brand.XIAOMI -> "找到本应用 → 打开「自启动」；再进 省电策略 → 无限制；最近任务下拉「锁定」"
        Brand.HUAWEI, Brand.HONOR -> "找到本应用 → 关闭「自动管理」→ 自启动/关联启动/后台活动 三个开关全开"
        Brand.OPPO -> "允许本应用「自启动」；再到 电池 → 应用速冻/智能省电 里关闭对本应用的限制"
        Brand.VIVO -> "允许本应用「关联启动/后台弹出」；再到 电池 → 后台高耗电 允许本应用"
        Brand.SAMSUNG -> "电池 → 后台使用限制 → 把本应用从「深度休眠」列表移除"
        Brand.MEIZU -> "后台管理里允许本应用自启动与后台运行"
        Brand.OTHER -> "允许本应用自启动与后台运行"
    }

    private fun brandName(): String = when (brand()) {
        Brand.XIAOMI -> "小米/红米"
        Brand.HUAWEI -> "华为"
        Brand.HONOR -> "荣耀"
        Brand.OPPO -> "OPPO/一加/真我"
        Brand.VIVO -> "vivo/iQOO"
        Brand.SAMSUNG -> "三星"
        Brand.MEIZU -> "魅族"
        Brand.OTHER -> "本机"
    }

    private fun cn(pkg: String, cls: String) = ComponentName(pkg, cls)
}
