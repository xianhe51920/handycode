import com.fairycrane.handycode.JdUrlPolicy;
import com.fairycrane.handycode.LogoutSites;
public class JdUrlPolicyTest {
 public static void main(String[] args){
  for(String u:new String[]{JdUrlPolicy.LOGIN,JdUrlPolicy.ORDERS,"https://JD.COM:443/path"})
   if(!JdUrlPolicy.allowed(u))throw new AssertionError("Valid JD URL rejected");
  for(String u:new String[]{"http://jd.com","https://jd.com.evil.invalid","https://eviljd.com","https://evil@jd.com","https://jd.com:444","javascript:alert(1)","file:///sdcard/a","https://jd.com\\@evil.invalid","https://jd.com/\n",null})
   if(JdUrlPolicy.allowed(u))throw new AssertionError("Untrusted URL accepted");
  if(!"jd.com".equals(LogoutSites.forPage(2)[0]))throw new AssertionError("JD logout scope");
  for(int i=0;i<2;i++)for(String site:LogoutSites.forPage(i))if(site.equals("jd.com"))throw new AssertionError("Shared logout scope");
  System.out.println("JD URL and logout isolation checks passed");
 }
}
