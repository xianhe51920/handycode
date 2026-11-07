package com.fairycrane.handycode;
import java.net.URI;
import java.util.Locale;
/** HTTPS-only pickup platform navigation policies (JD, SF Express, Douyin). */
public final class JdUrlPolicy {
 private JdUrlPolicy() {}
 public static final String ORDERS="https://trade.m.jd.com/order/orderlist_jdm.shtml?sceneval=2";
 public static final String MY_HOME="https://my.m.jd.com/index.html?sceneval=2&sourceType=home_m";
 public static final String LOGIN="https://plogin.m.jd.com/login/login?appid=300&returnurl=https%3A%2F%2Ftrade.m.jd.com%2Forder%2Forderlist_jdm.shtml%3Fsceneval%3D2";
 public static final String SF_ORDERS="https://www.sf-express.com/chn/sc/waybill/list";
 public static final String DY_ORDERS="https://haohuo.jinritemai.com/views/neworder/center";
 public static final String DY_LOGIN="https://www.douyin.com/user/self";
 private static final String[] JD_HOSTS={"jd.com","3.cn","360buyimg.com","jdpay.com","jingdong.com","jdh.com","jkcsjd.com"};
 private static final String[] DY_HOSTS={"jinritemai.com","snssdk.com","douyin.com","toutiao.com","ecombdstatic.com","douyinstatic.com","byteimg.com","bytescm.com","ibytedapm.com","zijieapi.com","bytedance.com","yhgfb-cn-static.com","ibytedtos.com","bytetos.com","bytegoofy.com","bytegecko.com","bytednsdoc.com","pstatp.com","amemv.com","toutiaoapi.com","zijiecdn.com"};

 private static String validHttpsHost(String raw) {
  if(raw==null||raw.isEmpty()||raw.length()>8192)return null;
  for(int i=0;i<raw.length();i++)if(Character.isWhitespace(raw.charAt(i))||Character.isISOControl(raw.charAt(i))||raw.charAt(i)=='\\')return null;
  try {
   URI u=new URI(raw);String h=u.getHost();
   if(!"https".equalsIgnoreCase(u.getScheme())||u.getRawUserInfo()!=null||(u.getPort()!=-1&&u.getPort()!=443)||h==null)return null;
   return h.toLowerCase(Locale.ROOT);
  }catch(Exception invalid){return null;}
 }

 public static boolean allowed(String raw) {
  String h=validHttpsHost(raw);
  if(h==null)return false;
  for(String root:JD_HOSTS)if(h.equals(root)||h.endsWith("."+root))return true;
  return false;
 }

 public static boolean allowedSf(String raw) {
  String h=validHttpsHost(raw);
  return h!=null&&(h.equals("sf-express.com")||h.endsWith(".sf-express.com"));
 }

 public static boolean allowedDy(String raw) {
  String h=validHttpsHost(raw);
  if(h==null)return false;
  for(String root:DY_HOSTS)if(h.equals(root)||h.endsWith("."+root))return true;
  return false;
 }
}