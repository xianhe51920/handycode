package com.fairycrane.handycode;
import java.net.URI;
import java.util.Locale;
/** HTTPS-only JD navigation. Kept separate from the existing platform policies. */
public final class JdUrlPolicy {
 private JdUrlPolicy() {}
 public static final String ORDERS="https://trade.m.jd.com/order/orderlist_jdm.shtml";
 public static final String IDENTITY="https://convenient-h5.jd.com/identityCode?channel=xueshengjirukou";
 public static boolean isIdentity(String raw) {
  if (!allowed(raw)) return false;
  try {
   URI u = new URI(raw);
   return "convenient-h5.jd.com".equalsIgnoreCase(u.getHost()) && "/identityCode".equals(u.getPath());
  } catch (Exception invalid) { return false; }
 }
 public static final String LOGIN="https://plogin.m.jd.com/login/login?appid=100&returnurl=https%3A%2F%2Ftrade.m.jd.com%2Forder%2Forderlist_jdm.shtml";
 public static boolean allowed(String raw) {
  if(raw==null||raw.length()>8192)return false;
  for(int i=0;i<raw.length();i++)if(Character.isWhitespace(raw.charAt(i))||Character.isISOControl(raw.charAt(i))||raw.charAt(i)=='\\')return false;
  try {
   URI u=new URI(raw);String h=u.getHost();
   if(!"https".equalsIgnoreCase(u.getScheme())||u.getRawUserInfo()!=null||(u.getPort()!=-1&&u.getPort()!=443)||h==null)return false;
   h=h.toLowerCase(Locale.ROOT);return h.equals("jd.com")||h.endsWith(".jd.com");
  }catch(Exception invalid){return false;}
 }
}
