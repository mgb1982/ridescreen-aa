// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of RideScreen AA (fork of OpenCfMoto). Free software under the GNU AGPL v3 or later.
package dev.zanderp.opencfmoto

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

/**
 * 1×1 home-screen widget (idea from Luis Gil): one tap starts Connect with the saved bike. A tap
 * while it is still connecting cancels, but only after a short lock (see MainActivity) so a brush
 * while pocketing the phone doesn't undo it.
 */
class ConnectWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        val launch = Intent(ctx, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_WIDGET_CONNECT, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pi = PendingIntent.getActivity(
            ctx, 4311, launch, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        for (id in ids) {
            val views = RemoteViews(ctx.packageName, R.layout.widget_connect)
            views.setOnClickPendingIntent(R.id.wc_root, pi)
            mgr.updateAppWidget(id, views)
        }
    }
}
