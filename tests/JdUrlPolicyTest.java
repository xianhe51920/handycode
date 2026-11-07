import com.fairycrane.handycode.JdUrlPolicy;
import com.fairycrane.handycode.LogoutSites;
import java.util.Arrays;
public class JdUrlPolicyTest {
 public static void main(String[] args){
  for(String u:new String[]{JdUrlPolicy.LOGIN,JdUrlPolicy.ORDERS,JdUrlPolicy.MY_HOME,"https://JD.COM:443/path"})
   if(!JdUrlPolicy.allowed(u))throw new AssertionError("Valid JD URL rejected");
  for(String u:new String[]{"http://jd.com","https://jd.com.evil.invalid","https://eviljd.com","https://evil@jd.com","https://jd.com:444","javascript:alert(1)","file:///sdcard/a","https://jd.com\\@evil.invalid","https://jd.com/\n",null})
   if(JdUrlPolicy.allowed(u))throw new AssertionError("Untrusted URL accepted");
  for(String u:new String[]{JdUrlPolicy.SF_ORDERS,"https://mcs-mimp-web.sf-express.com/waybill"})
   if(!JdUrlPolicy.allowedSf(u))throw new AssertionError("Valid SF URL rejected");
  for(String u:new String[]{"http://www.sf-express.com","https://sf-express.com.evil.invalid",null})
   if(JdUrlPolicy.allowedSf(u))throw new AssertionError("Untrusted SF URL accepted");
  if(!"https://www.douyin.com/user/self".equals(JdUrlPolicy.DY_LOGIN))
   throw new AssertionError("DY_LOGIN must use official www.douyin.com/user/self");
  for(String u:new String[]{JdUrlPolicy.DY_ORDERS,JdUrlPolicy.DY_LOGIN,"https://ec.snssdk.com/order/page","https://sso.douyin.com/passport/web/sms_login/","https://lf-dy-sourcecdn-tos.bytegecko.com/obj/byte-gurd-source/1.js"})
   if(!JdUrlPolicy.allowedDy(u))throw new AssertionError("Valid Douyin URL rejected: "+u);
  for(String u:new String[]{"http://haohuo.jinritemai.com","http://www.douyin.com/user/self","https://douyin.com.evil.invalid","https://snssdk.com.evil.invalid",null})
   if(JdUrlPolicy.allowedDy(u))throw new AssertionError("Untrusted Douyin URL accepted: "+u);
  if(!"jd.com".equals(LogoutSites.forPage(2)[0]))throw new AssertionError("JD logout scope");
  if(!Arrays.asList(LogoutSites.forPickupProvider(4)).contains("douyin.com"))
   throw new AssertionError("Douyin pickup logout must clear douyin.com");
  for(int i=0;i<2;i++)for(String site:LogoutSites.forPage(i))if(site.equals("jd.com"))throw new AssertionError("Shared logout scope");
  System.out.println("JD, SF and Douyin URL and logout isolation checks passed");
 }
}
