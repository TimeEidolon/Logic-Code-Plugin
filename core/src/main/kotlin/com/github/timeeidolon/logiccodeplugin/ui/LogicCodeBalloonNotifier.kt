package com.github.timeeidolon.logiccodeplugin.ui

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project

object LogicCodeBalloonNotifier {

    const val NOTIFICATION_GROUP_ID: String = "com.github.timeeidolon.logiccodeplugin.notifications"

    fun notifyInfo(project: Project, title: String, htmlOrPlainBody: String) {
        val group = NotificationGroupManager.getInstance().getNotificationGroup(NOTIFICATION_GROUP_ID) ?: return
        group.createNotification(title, htmlOrPlainBody, NotificationType.INFORMATION).notify(project)
    }
}
