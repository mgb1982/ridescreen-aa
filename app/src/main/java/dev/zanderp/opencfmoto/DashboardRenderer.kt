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

    fun render(ctx:Context,w:Int,h:Int,d:DashboardMode.Data,light:Boolean,design:DashboardMode.Design,visible:Set<String>):Bitmap {
        var b=bmp
        if(b==null||b.width!=w||b.height!=h){b?.recycle();b=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);bmp=b;canvas=Canvas(b)}
        val c=canvas!!;val p=if(light)LIGHT else DARK;c.drawColor(p.bg)
        val s=minOf(w/DW,h/DH);c.save();c.translate((w-DW*s)/2f,(h-DH*s)/2f);c.scale(s,s)
        if(design==DashboardMode.Design.GAUGES) drawGauges(ctx,c,d,p,visible) else drawBars(ctx,c,d,p,visible)
        c.restore();return b
    }

    private data class Metric(val id:String,val label:String,val value:Double?,val unit:String,val max:Double=100.0)
    private fun metrics(d:DashboardMode.Data)=listOf(
        Metric("rpm","RPM",d.rpm,"rpm",10000.0), Metric("speed","SPEED",d.speedKmh,"km/h",180.0),
        Metric("coolant","COOLANT",d.coolant,"°C",130.0), Metric("throttle","THROTTLE",d.throttle,"%",100.0),
        Metric("volt","VOLTAGE",d.volt,"V",16.0), Metric("load","ENGINE LOAD",d.load,"%",100.0),
        Metric("intake","INTAKE AIR",d.intake,"°C",80.0), Metric("baro","BARO",d.baro,"kPa",120.0),
        Metric("absolute","ABS LOAD",d.absoluteLoad,"% ",260.0), Metric("relative","REL THROTTLE",d.relativeThrottle,"%",100.0)
    )
    private fun fmt(m:Metric):String=m.value?.let{if(m.unit=="V")String.format(Locale.US,"%.1f",it) else String.format(Locale.US,"%.0f",it)}?:"—"

    private fun drawBars(ctx:Context,c:Canvas,d:DashboardMode.Data,p:Palette,visible:Set<String>){
        val all=metrics(d).filter{it.id in visible}
        val rpm=all.firstOrNull{it.id=="rpm"}
        if(rpm!=null){label(c,fmt(rpm)+" rpm",35f,83f,22f,p.fg,bold);fill.color=p.track;rect.set(22f,18f,778f,49f);c.drawRoundRect(rect,5f,5f,fill)
            fill.color=p.accent;rect.set(22f,18f,22f+756f*((rpm.value?:0.0)/10000).toFloat().coerceIn(0f,1f),49f);c.drawRoundRect(rect,5f,5f,fill)}
        val speed=all.firstOrNull{it.id=="speed"}
        if(speed!=null){label(c,fmt(speed),34f,270f,150f,p.fg,bold);label(c,"km/h"+if(d.speedFromGps)" · GPS" else "",40f,315f,24f,p.muted,plain)}
        val rest=all.filter{it.id!="rpm"&&it.id!="speed"}
        rest.forEachIndexed{i,m->
            val col=i%2;val row=i/2;val x=400f+col*188f;val y=64f+row*82f
            fill.color=p.tile;rect.set(x,y,x+176f,y+72f);c.drawRoundRect(rect,10f,10f,fill)
            label(c,m.label,x+12f,y+23f,14f,p.muted,plain);label(c,fmt(m)+" "+m.unit,x+12f,y+57f,29f,p.fg,bold)
            fill.color=p.track;rect.set(x+12f,y+64f,x+164f,y+69f);c.drawRoundRect(rect,3f,3f,fill)
            m.value?.let{fill.color=if(m.id=="coolant"&&it>=105)p.red else p.accent;rect.set(x+12f,y+64f,x+12f+152f*(it/m.max).toFloat().coerceIn(0f,1f),y+69f);c.drawRoundRect(rect,3f,3f,fill)}
        }
        if(!d.obdLive)label(c,ctx.getString(R.string.dash_no_obd),35f,385f,19f,p.muted,plain)
    }

    private fun drawGauges(ctx:Context,c:Canvas,d:DashboardMode.Data,p:Palette,visible:Set<String>){
        val all=metrics(d).filter{it.id in visible}
        // Large tachometer; remaining readings use compact motorcycle-style dials.
        val rpm=all.firstOrNull{it.id=="rpm"}
        rpm?.let{gauge(c,it,160f,220f,153f,p,true)}
        val others=all.filter{it.id!="rpm"}
        val positions= arrayOf(Pair(402f,78f),Pair(545f,78f),Pair(688f,78f),Pair(402f,208f),Pair(545f,208f),Pair(688f,208f),Pair(402f,338f),Pair(545f,338f),Pair(688f,338f))
        // On an 800×416 panel, use three columns by two rows plus a narrow overflow column;
        // compact circles keep units and readings legible while preserving the analog instrument feel.
        others.take(9).forEachIndexed{i,m->val (x,y)=positions[i];gauge(c,m,x,y,54f,p,false)}
        
        if(!d.obdLive)label(c,ctx.getString(R.string.dash_no_obd),34f,402f,16f,p.muted,plain)
    }

    private fun gauge(c:Canvas,m:Metric,cx:Float,cy:Float,r:Float,p:Palette,major:Boolean){
        fill.color=0xFF090D0F.toInt();c.drawCircle(cx,cy,r,fill)
        stroke.strokeWidth=if(major)8f else 5f;stroke.color=0xFFBFC5C6.toInt();c.drawCircle(cx,cy,r-2f,stroke)
        stroke.strokeWidth=if(major)4f else 2.5f;stroke.color=p.accent;rect.set(cx-r+9,cy-r+9,cx+r-9,cy+r-9);c.drawArc(rect,135f,270f,false,stroke)
        val frac=((m.value?:0.0)/m.max).toFloat().coerceIn(0f,1f)
        stroke.strokeWidth=if(major)4f else 2.5f;stroke.color=if(m.id=="coolant"&&frac>.8f)p.red else p.fg;c.drawArc(rect,135f,270f*frac,false,stroke)
        val titleSize=if(major)18f else if(r>55)11f else 8f
        val valueSize=if(major)43f else if(r>55)20f else 15f
        centered(c,m.label,cx,cy-r*.38f,titleSize,p.muted,plain)
        centered(c,fmt(m),cx,cy+valueSize*.18f,valueSize,p.fg,bold)
        centered(c,m.unit.trim(),cx,cy+r*.47f,if(major)15f else 10f,p.muted,plain)
        if(m.value!=null){val angle=Math.toRadians(135.0+270.0*frac);stroke.strokeWidth=2f;stroke.color=p.red;c.drawLine(cx,cy,cx+(r*.60f*kotlin.math.cos(angle)).toFloat(),cy+(r*.60f*kotlin.math.sin(angle)).toFloat(),stroke)}
    }
    private fun label(c:Canvas,s:String,x:Float,y:Float,size:Float,color:Int,face:Typeface){text.typeface=face;text.textSize=size;text.color=color;c.drawText(s,x,y,text)}
    private fun centered(c:Canvas,s:String,x:Float,y:Float,size:Float,color:Int,face:Typeface){text.typeface=face;text.textSize=size;text.color=color;c.drawText(s,x-text.measureText(s)/2f,y,text)}
}
