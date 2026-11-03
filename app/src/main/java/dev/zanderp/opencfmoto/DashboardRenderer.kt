// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of RideScreen AA (fork of OpenCfMoto). Free software under the GNU AGPL v3 or later.
package dev.zanderp.opencfmoto

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import java.util.Locale

/** Two configurable OBD layouts rendered on the dash's 800×416 canvas. */
internal object DashboardRenderer {
    private const val DW = 800f
    private const val DH = 416f
    private class Palette(val bg:Int,val fg:Int,val muted:Int,val track:Int,val tile:Int,val accent:Int,val red:Int)
    private val DARK=Palette(0xFF101713.toInt(),0xFFF4F5F0.toInt(),0xFF9AA69D.toInt(),0xFF28342D.toInt(),0xFF1B2720.toInt(),0xFFD5AA48.toInt(),0xFFE44840.toInt())
    private val LIGHT=Palette(0xFFEAE9E1.toInt(),0xFF15191A.toInt(),0xFF58625D.toInt(),0xFFC7C9C1.toInt(),0xFFD9DCD4.toInt(),0xFF987021.toInt(),0xFFC93630.toInt())
    private var bmp:Bitmap?=null
    private var canvas:Canvas?=null
    private val fill=Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke=Paint(Paint.ANTI_ALIAS_FLAG).apply{style=Paint.Style.STROKE;strokeCap=Paint.Cap.ROUND}
    private val text=Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect=RectF()
    private val bold=Typeface.create("sans-serif-condensed",Typeface.BOLD)
    private val plain=Typeface.create("sans-serif",Typeface.NORMAL)

    fun render(ctx:Context,w:Int,h:Int,d:DashboardMode.Data,light:Boolean,visible:Set<String>):Bitmap {
        var b=bmp
        if(b==null||b.width!=w||b.height!=h){b?.recycle();b=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);bmp=b;canvas=Canvas(b)}
        val c=canvas!!;val p=if(light)LIGHT else DARK;c.drawColor(p.bg)
        val s=minOf(w/DW,h/DH);c.save();c.translate((w-DW*s)/2f,(h-DH*s)/2f);c.scale(s,s)
        drawRacing(ctx,c,d,p,visible)
        drawShiftLeds(c, d.rpm, p)
        c.restore();return b
    }

    /** Ten independent shift lamps. The transition LEDs are deliberately placed exactly at 6,000 and 8,500 rpm. */
    private fun drawShiftLeds(c: Canvas, rpm: Double?, p: Palette) {
        val thresholds = doubleArrayOf(600.0, 1_600.0, 2_600.0, 3_600.0, 4_600.0, 5_600.0, 6_000.0, 7_250.0, 8_500.0, 10_000.0)
        val lampW = 34f
        val lampH = 10f
        val gap = 10f
        val totalW = thresholds.size * lampW + (thresholds.size - 1) * gap
        val left = (DW - totalW) / 2f
        val top = 8f
        thresholds.forEachIndexed { index, threshold ->
            val color = when {
                index >= 8 -> android.graphics.Color.rgb(232, 55, 48)
                index >= 6 -> android.graphics.Color.rgb(239, 198, 47)
                else -> android.graphics.Color.rgb(41, 205, 91)
            }
            fill.color = if (rpm != null && rpm >= threshold) color else p.track
            rect.set(left + index * (lampW + gap), top, left + index * (lampW + gap) + lampW, top + lampH)
            c.drawRoundRect(rect, lampH / 2f, lampH / 2f, fill)
        }
    }

    private data class Metric(val id:String,val label:String,val value:Double?,val unit:String,val max:Double=100.0)
    private fun metrics(d:DashboardMode.Data)=listOf(
        Metric("rpm","RPM",d.rpm,"rpm",10000.0), Metric("speed","SPEED",d.speedKmh,"km/h",180.0),
        Metric("throttle","THROTTLE",d.relativeThrottle,"%",100.0),
        Metric("coolant","COOLANT",d.coolant,"°C",130.0),
        Metric("volt","VOLTAGE",d.volt,"V",16.0), Metric("load","ENGINE LOAD",d.load,"%",100.0),
        Metric("intake","INTAKE AIR",d.intake,"°C",80.0), Metric("baro","BARO",d.baro,"kPa",120.0),
        Metric("absolute","ABS LOAD",d.absoluteLoad,"%",260.0)
    )
    private fun fmt(m:Metric):String=m.value?.let{if(m.unit=="V")String.format(Locale.US,"%.1f",it) else String.format(Locale.US,"%.0f",it)}?:"—"

    private fun drawRacing(ctx:Context,c:Canvas,d:DashboardMode.Data,p:Palette,visible:Set<String>){
        drawShiftLeds(c,d.rpm,p)
        val all=metrics(d).filter{it.id in visible}
        val byId=all.associateBy{it.id}
        drawDial(c,byId["speed"],150f,112f,66f,p,if(d.speedFromGps)"GPS" else null)
        drawDial(c,byId["rpm"],400f,112f,66f,p,null)
        drawDial(c,byId["throttle"],650f,112f,66f,p,null)

        val cards=all.filter{it.id !in setOf("speed","rpm","throttle")}
        val cols=3
        val gapX=10f
        val gapY=8f
        val cardW=(DW-2f*42f-gapX*(cols-1))/cols
        val cardH=if(cards.size<=cols) 96f else 94f
        cards.forEachIndexed{i,m->
            val col=i%cols;val row=i/cols
            val x=42f+col*(cardW+gapX);val y=195f+row*(cardH+gapY)
            drawCard(c,m,x,y,cardW,cardH,p)
        }
        if(!d.obdLive)label(c,ctx.getString(R.string.dash_no_obd),42f,407f,14f,p.muted,plain)
    }

    private fun drawDial(c:Canvas,m:Metric?,cx:Float,cy:Float,r:Float,p:Palette,hint:String?){
        if(m==null)return
        fill.color=0xFF111719.toInt();c.drawCircle(cx,cy,r,fill)
        stroke.strokeWidth=2f;stroke.color=0xFF414B4D.toInt();c.drawCircle(cx,cy,r-1f,stroke)
        rect.set(cx-r+7f,cy-r+7f,cx+r-7f,cy+r-7f)
        stroke.strokeWidth=5f;stroke.color=p.track;c.drawArc(rect,135f,270f,false,stroke)
        val fraction=((m.value?:0.0)/m.max).toFloat().coerceIn(0f,1f)
        stroke.strokeWidth=5f;stroke.color=if(m.id=="rpm"&&fraction>=.88f)p.red else p.accent
        c.drawArc(rect,135f,270f*fraction,false,stroke)
        centered(c,m.label,cx,cy-18f,10f,p.muted,plain)
        centered(c,fmt(m),cx,cy+10f,25f,p.fg,bold)
        centered(c,hint?.let{"$it · ${m.unit}"}?:m.unit,cx,cy+29f,10f,p.muted,plain)
    }

    private fun drawCard(c:Canvas,m:Metric,x:Float,y:Float,w:Float,h:Float,p:Palette){
        fill.color=p.tile;rect.set(x,y,x+w,y+h);c.drawRoundRect(rect,9f,9f,fill)
        label(c,m.label,x+12f,y+21f,10f,p.muted,plain)
        label(c,fmt(m),x+12f,y+h-19f,25f,p.fg,bold)
        label(c,m.unit,x+18f+text.measureText(fmt(m)),y+h-19f,12f,p.muted,plain)
        m.value?.let{
            fill.color=if(m.id=="coolant"&&it>=DashboardMode.COOLANT_AMBER)p.red else p.accent
            val ratio=(it/m.max).toFloat().coerceIn(0f,1f)
            val barY=y+h-7f
            fill.color=p.track;rect.set(x+11f,barY,x+w-11f,barY+3f);c.drawRoundRect(rect,2f,2f,fill)
            fill.color=if(m.id=="coolant"&&it>=DashboardMode.COOLANT_AMBER)p.red else p.accent
            rect.set(x+11f,barY,x+11f+(w-22f)*ratio,barY+3f);c.drawRoundRect(rect,2f,2f,fill)
        }
    }
    private fun label(c:Canvas,s:String,x:Float,y:Float,size:Float,color:Int,face:Typeface){text.typeface=face;text.textSize=size;text.color=color;c.drawText(s,x,y,text)}
    private fun centered(c:Canvas,s:String,x:Float,y:Float,size:Float,color:Int,face:Typeface){text.typeface=face;text.textSize=size;text.color=color;c.drawText(s,x-text.measureText(s)/2f,y,text)}
}
