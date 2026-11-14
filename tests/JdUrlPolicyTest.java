import com.fairycrane.handycode.JdUrlPolicy;
import com.fairycrane.handycode.LogoutSites;
public class JdUrlPolicyTest {
 public static void main(String[] args){
  for(String u:new String[]{JdUrlPolicy.LOGIN,JdUrlPolicy.ORDERS,JdUrlPolicy.IDENTITY,"https://JD.COM:443/path"})
   if(!JdUrlPolicy.allowed(u))throw new AssertionError("Valid JD URL rejected");
  for(String u:new String[]{"http://jd.com","https://jd.com.evil.invalid","https://eviljd.com","https://evil@jd.com","https://jd.com:444","javascript:alert(1)","file:///sdcard/a","https://jd.com\\@evil.invalid","https://jd.com/\n",null})
   if(JdUrlPolicy.allowed(u))throw new AssertionError("Untrusted URL accepted");
  if(!"jd.com".equals(LogoutSites.forPage(3)[0]))throw new AssertionError("JD logout scope");
  if(!JdUrlPolicy.isIdentity(JdUrlPolicy.IDENTITY))throw new AssertionError("JD identity route");
  for(String u:new String[]{"https://convenient-h5.jd.com/other","https://convenient-h5.jd.com.evil.invalid/identityCode","http://convenient-h5.jd.com/identityCode",null})
   if(JdUrlPolicy.isIdentity(u))throw new AssertionError("Untrusted identity route");
  for(int i=1;i<=2;i++)for(String site:LogoutSites.forPage(i))if(site.equals("jd.com"))throw new AssertionError("Shared logout scope");
  System.out.println("JD URL and logout isolation checks passed");
 }
}
