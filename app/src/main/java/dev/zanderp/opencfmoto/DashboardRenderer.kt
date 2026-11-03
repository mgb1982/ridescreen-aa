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
/** Three selectable OBD dashboards rendered on the dash's 800×416 canvas. */
internal object DashboardRenderer {
 private const val W=800f; private const val H=416f
 private class Palette(val bg:Int,val fg:Int,val muted:Int,val track:Int,val tile:Int,val accent:Int,val red:Int)
 private val sd=Palette(0xFF101713.toInt(),0xFFF4F5F0.toInt(),0xFF9AA69D.toInt(),0xFF28342D.toInt(),0xFF1B2720.toInt(),0xFFD5AA48.toInt(),0xFFE44840.toInt())
 private val sl=Palette(0xFFEAE9E1.toInt(),0xFF15191A.toInt(),0xFF58625D.toInt(),0xFFC7C9C1.toInt(),0xFFD9DCD4.toInt(),0xFF987021.toInt(),0xFFC93630.toInt())
 private val rd=Palette(0xFF090B0F.toInt(),0xFFF8F8FA.toInt(),0xFF9EA4AE.toInt(),0xFF30343D.toInt(),0xFF171A20.toInt(),0xFFFF2638.toInt(),0xFFFF2638.toInt())
 private val rl=Palette(0xFFF0F1F4.toInt(),0xFF11141A.toInt(),0xFF555B66.toInt(),0xFFC8CBD2.toInt(),0xFFFFFFFF.toInt(),0xFFD7192D.toInt(),0xFFD7192D.toInt())
 private val td=Palette(0xFF080F12.toInt(),0xFFEAFBF8.toInt(),0xFF8DA9A4.toInt(),0xFF203A38.toInt(),0xFF102321.toInt(),0xFF38D6B2.toInt(),0xFFFF625A.toInt())
 private val tl=Palette(0xFFEAF3EF.toInt(),0xFF102622.toInt(),0xFF58736D.toInt(),0xFFC1D4CE.toInt(),0xFFF8FCF9.toInt(),0xFF138D75.toInt(),0xFFD43B36.toInt())
 private var bmp:Bitmap?=null; private var canvas:Canvas?=null
 private val fill=Paint(Paint.ANTI_ALIAS_FLAG); private val stroke=Paint(Paint.ANTI_ALIAS_FLAG).apply{style=Paint.Style.STROKE;strokeCap=Paint.Cap.ROUND}
 private val text=Paint(Paint.ANTI_ALIAS_FLAG); private val rect=RectF()
 private val bold=Typeface.create("sans-serif-condensed",Typeface.BOLD); private val plain=Typeface.create("sans-serif",Typeface.NORMAL)
 private data class M(val id:String,val name:String,val v:Double?,val unit:String,val max:Double=100.0)
 private fun metrics(d:DashboardMode.Data)=listOf(M("speed","SPEED",d.speedKmh,"km/h",240.0),M("rpm","RPM",d.rpm,"rpm",10000.0),M("throttle","THROTTLE",d.relativeThrottle,"%",100.0),M("coolant","COOLANT",d.coolant,"°C",130.0),M("volt","VOLTAGE",d.volt,"V",16.0),M("load","ENGINE LOAD",d.load,"%",100.0),M("intake","INTAKE AIR",d.intake,"°C",80.0),M("baro","MANIFOLD",d.baro,"kPa",120.0),M("absolute","ABS LOAD",d.absoluteLoad,"%",260.0))
 private fun fmt(m:M?)=m?.v?.let{if(m.unit=="V")String.format(Locale.US,"%.1f",it) else String.format(Locale.US,"%.0f",it)}?:"—"
 fun render(ctx:Context,w:Int,h:Int,d:DashboardMode.Data,light:Boolean,design:DashboardMode.Design,visible:Set<String>):Bitmap {
  var b=bmp;if(b==null||b.width!=w||b.height!=h){b?.recycle();b=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);bmp=b;canvas=Canvas(b)}
  val c=canvas!!;val p=when(design){DashboardMode.Design.SPORT->if(light)sl else sd;DashboardMode.Design.RACING->if(light)rl else rd;DashboardMode.Design.TOURING->if(light)tl else td};c.drawColor(p.bg)
  val s=minOf(w/W,h/H);c.save();c.translate((w-W*s)/2f,(h-H*s)/2f);c.scale(s,s)
  val all=metrics(d).filter{it.id in visible}.associateBy{it.id}
  when(design){DashboardMode.Design.SPORT->sport(c,d,p,all);DashboardMode.Design.RACING->racing(c,d,p,all);DashboardMode.Design.TOURING->tour(c,d,p,all)}
  if(!d.obdLive)label(c,ctx.getString(R.string.dash_no_obd),12f,408f,12f,p.muted,plain)
  c.restore();return b
 }
 private fun leds(c:Canvas,rpm:Double?,p:Palette,y:Float=12f){val n=10;val lw=38f;val gap=8f;val x=(W-(n*lw+(n-1)*gap))/2f;for(i in 0 until n){fill.color=if(rpm!=null&&rpm>=(i+1)*1000.0){if(i>7)p.red else p.accent}else p.track;rect.set(x+i*(lw+gap),y,x+i*(lw+gap)+lw,y+9);c.drawRoundRect(rect,5f,5f,fill)}}
 private fun sport(c:Canvas,d:DashboardMode.Data,p:Palette,a:Map<String,M>){
  leds(c,d.rpm,p);dial(c,a["speed"],178f,206f,148f,p,if(d.speedFromGps)"GPS" else null,true)
  dial(c,a["rpm"],505f,103f,57f,p,null,false);dial(c,a["throttle"],670f,103f,57f,p,null,false)
  cards(c,listOf("coolant","volt","load","intake","baro","absolute").mapNotNull{a[it]},365f,173f,2,197f,62f,p)
 }
 private fun racing(c:Canvas,d:DashboardMode.Data,p:Palette,a:Map<String,M>){
  leds(c,d.rpm,p,8f)
  label(c,"RPM",38f,54f,15f,p.fg,bold)
  val bx=38f;val by=67f;val bw=724f;val count=14;val gap=4f;val seg=(bw-gap*(count-1))/count
  for(i in 0 until count){
   val left=bx+i*(seg+gap);val threshold=(i+1)*10000.0/count
   fill.color=if(d.rpm!=null&&d.rpm>=threshold){if(i>=11)p.red else p.accent}else p.track
   rect.set(left,by,left+seg,by+34f);c.drawRoundRect(rect,3f,3f,fill)
  }
  center(c,fmt(a["rpm"]),400f,96f,27f,p.fg,bold)
  for(i in 0..7)center(c,(i*2).toString(),bx+bw*i/7f,119f,10f,p.muted,plain)
  // Motorsport speed plate with clipped corners, red keyline and segmented accents.
  val px=34f;val py=152f;val pw=365f;val ph=132f
  val shape=android.graphics.Path().apply{moveTo(px+16f,py);lineTo(px+pw-14f,py);lineTo(px+pw,py+15f);lineTo(px+pw-12f,py+ph);lineTo(px,py+ph);lineTo(px+12f,py+ph-14f);close()}
  fill.color=p.tile;c.drawPath(shape,fill);stroke.color=p.accent;stroke.strokeWidth=2f;c.drawPath(shape,stroke)
  label(c,fmt(a["speed"]),60f,239f,80f,p.fg,bold)
  label(c,"km/h"+if(d.speedFromGps)" · GPS" else "",67f,268f,17f,p.muted,plain)
  for(i in 0..4){val sx=320f+i*13f;stroke.color=p.accent;stroke.strokeWidth=3f;c.drawLine(sx,266f,sx+10f,253f,stroke)}
  dial(c,a["throttle"],625f,218f,79f,p,null,false)
  cards(c,listOf("coolant","volt","load","intake","baro").mapNotNull{a[it]},40f,304f,3,238f,45f,p)
 }
 private fun tour(c:Canvas,d:DashboardMode.Data,p:Palette,a:Map<String,M>){
  leds(c,d.rpm,p)
  val cx=180f;val cy=179f;val r=134f
  rect.set(cx-r,cy-r,cx+r,cy+r);stroke.color=p.track;stroke.strokeWidth=16f;c.drawArc(rect,135f,270f,false,stroke)
  val f=((a["speed"]?.v?:0.0)/(a["speed"]?.max?:240.0)).toFloat().coerceIn(0f,1f);stroke.color=p.accent;c.drawArc(rect,135f,270f*f,false,stroke)
  for(i in 0..24){
   val angle=Math.toRadians(135.0+270.0*i/24);val major=i%4==0
   val r1=r-if(major)22f else 13f;val r2=r-2f
   stroke.color=if(major)p.fg else p.muted;stroke.strokeWidth=if(major)2f else 1f
   c.drawLine(cx+Math.cos(angle).toFloat()*r1,cy+Math.sin(angle).toFloat()*r1,cx+Math.cos(angle).toFloat()*r2,cy+Math.sin(angle).toFloat()*r2,stroke)
   if(major)center(c,(i*10).toString(),cx+Math.cos(angle).toFloat()*(r-36f),cy+Math.sin(angle).toFloat()*(r-36f)+4f,9f,p.muted,plain)
  }
  center(c,fmt(a["speed"]),cx,cy+15f,58f,p.fg,bold)
  center(c,"km/h"+if(d.speedFromGps)" · GPS" else "",cx,cy+43f,13f,p.muted,plain)
  label(c,"RPM",376f,61f,14f,p.muted,bold)
  val bx=390f;val by=72f;val bw=34f;val bh=202f;val n=12
  for(i in 0 until n){val top=by+bh-(i+1)*(bh/n);val threshold=(i+1)*10000.0/n
   fill.color=if(d.rpm!=null&&d.rpm>=threshold){if(i>=10)p.red else p.accent}else p.track
   rect.set(bx,top+2f,bx+bw,top+bh/n-2f);c.drawRoundRect(rect,4f,4f,fill)
  }
  for(i in 0..5)label(c,(i*2).toString(),434f,by+bh-i*(bh/5f)+4f,9f,p.muted,plain)
  center(c,fmt(a["rpm"]),489f,193f,22f,p.fg,bold)
  dial(c,a["throttle"],670f,132f,69f,p,null,false)
  cards(c,listOf("coolant","volt","load","intake","baro","absolute").mapNotNull{a[it]},35f,320f,3,240f,39f,p)
 }
 private fun dial(c:Canvas,m:M?,x:Float,y:Float,r:Float,p:Palette,hint:String?,large:Boolean){
  if(m==null)return
  fill.color=p.tile;c.drawCircle(x,y,r,fill)
  stroke.strokeWidth=if(large)2.5f else 1.8f;stroke.color=p.track;c.drawCircle(x,y,r-1f,stroke)
  rect.set(x-r+7,y-r+7,x+r-7,y+r-7)
  stroke.strokeWidth=if(large)8f else 5f;stroke.color=p.track;c.drawArc(rect,135f,270f,false,stroke)
  val f=((m.v?:0.0)/m.max).toFloat().coerceIn(0f,1f)
  stroke.color=if(m.id=="rpm"&&f>.88f)p.red else p.accent;c.drawArc(rect,135f,270f*f,false,stroke)
  val ticks=if(large)24 else 16
  for(i in 0..ticks){
   val angle=Math.toRadians(135.0+270.0*i/ticks)
   val major=if(large)i%4==0 else i%4==0
   val r1=r-if(major) (if(large)24f else 15f) else (if(large)16f else 11f)
   val r2=r-if(large)6f else 5f
   stroke.color=if(major)p.fg else p.muted;stroke.strokeWidth=if(major)2f else 1f
   c.drawLine(x+Math.cos(angle).toFloat()*r1,y+Math.sin(angle).toFloat()*r1,x+Math.cos(angle).toFloat()*r2,y+Math.sin(angle).toFloat()*r2,stroke)
   if(large&&major){
    val v=m.max*i/ticks
    val tx=x+Math.cos(angle).toFloat()*(r-36f);val ty=y+Math.sin(angle).toFloat()*(r-36f)+4f
    center(c,String.format(Locale.US,"%.0f",v),tx,ty,10f,p.muted,plain)
   }
  }
  val needle=Math.toRadians(135.0+270.0*f)
  stroke.color=p.red;stroke.strokeWidth=if(large)3f else 2f
  c.drawLine(x,y,x+Math.cos(needle).toFloat()*(r*.62f),y+Math.sin(needle).toFloat()*(r*.62f),stroke)
  fill.color=p.fg;c.drawCircle(x,y,if(large)5f else 3.5f,fill)
  center(c,m.name,x,y-r*.23f,if(large)16f else 9f,p.muted,bold)
  center(c,fmt(m),x,y+if(large)13f else 8f,if(large)45f else 22f,p.fg,bold)
  val unit=if(hint!=null)hint+" · "+m.unit else m.unit
  center(c,unit,x,y+r*.46f,if(large)13f else 9f,p.muted,plain)
 }
 private fun cards(c:Canvas,list:List<M>,x:Float,y:Float,cols:Int,w:Float,h:Float,p:Palette){
  val gap=7f
  list.forEachIndexed{i,m->
   val xx=x+(i%cols)*(w+gap);val yy=y+(i/cols)*(h+7f)
   fill.color=p.tile;rect.set(xx,yy,xx+w,yy+h);c.drawRoundRect(rect,8f,8f,fill)
   stroke.color=p.track;stroke.strokeWidth=1.2f;c.drawRoundRect(rect,8f,8f,stroke)
   fill.color=p.accent;c.drawCircle(xx+15f,yy+15f,8f,fill)
   center(c,m.name.take(1),xx+15f,yy+18f,9f,p.bg,bold)
   label(c,m.name,xx+29f,yy+18f,9f,p.muted,bold)
   val unitW=text.apply{typeface=plain;textSize=9f}.measureText(m.unit)
   label(c,fmt(m),xx+11f,yy+h-8f,if(h<45f)16f else 21f,p.fg,bold)
   if(h>=45f)label(c,m.unit,xx+w-unitW-9f,yy+h-8f,9f,p.muted,plain)
   if(m.id=="coolant"||m.id=="load"){
    val py=yy+h-3f;fill.color=p.track;rect.set(xx+9f,py,xx+w-9f,py+2f);c.drawRoundRect(rect,1f,1f,fill)
    m.v?.let{fill.color=if(m.id=="coolant"&&it>=DashboardMode.COOLANT_AMBER)p.red else p.accent;rect.set(xx+9f,py,xx+9f+(w-18f)*(it/m.max).toFloat().coerceIn(0f,1f),py+2f);c.drawRoundRect(rect,1f,1f,fill)}
   }
  }
 }
 private fun footer(c:Canvas,p:Palette){stroke.strokeWidth=2f;stroke.color=p.accent;c.drawLine(16f,390f,784f,390f,stroke);label(c,"FUEL     ▮ ▮ ▮ ▮ ▮             COOLANT                 SPEED",25f,410f,13f,p.muted,plain)}
 private fun label(c:Canvas,s:String,x:Float,y:Float,z:Float,color:Int,font:Typeface){text.typeface=font;text.textSize=z;text.color=color;c.drawText(s,x,y,text)}
 private fun center(c:Canvas,s:String,x:Float,y:Float,z:Float,color:Int,font:Typeface){text.typeface=font;text.textSize=z;text.color=color;c.drawText(s,x-text.measureText(s)/2,y,text)}
}