import com.fairycrane.handycode.PickupUrlPolicy;
import com.fairycrane.handycode.LogoutSites;
import java.util.Arrays;

public class PickupUrlPolicyTest {
 public static void main(String[] args) {
  if(!PickupUrlPolicy.allowed(PickupUrlPolicy.LOGIN)||!PickupUrlPolicy.allowedSf(PickupUrlPolicy.SF_ORDERS)||!PickupUrlPolicy.allowedDy(PickupUrlPolicy.DY_ORDERS))throw new AssertionError("Official entries");
  for(String url:new String[]{"http://www.sf-express.com/","https://sf-express.com.evil.invalid/","https://user@www.sf-express.com/","https://www.sf-express.com:444/","https://sf-express.com\\@evil.invalid/","https://www.sf-express.com/\n"})if(PickupUrlPolicy.allowedSf(url))throw new AssertionError("SF navigation");
  for(String url:new String[]{"http://www.douyin.com/","https://douyin.com.evil.invalid/","https://user@www.douyin.com/","https://www.douyin.com:444/","https://evil.invalid/"})if(PickupUrlPolicy.allowedDy(url))throw new AssertionError("DY navigation");
  if(PickupUrlPolicy.allowedSf("https://www.douyin.com/")||PickupUrlPolicy.allowedDy("https://www.sf-express.com/"))throw new AssertionError("Provider isolation");
  if(!Arrays.equals(LogoutSites.forPickupProvider(0),LogoutSites.forPage(1))||!Arrays.equals(LogoutSites.forPickupProvider(1),LogoutSites.forPage(2))||!Arrays.equals(LogoutSites.forPickupProvider(2),LogoutSites.forPage(3)))throw new AssertionError("Existing tab/provider mapping");
  if(!Arrays.equals(LogoutSites.forPickupProvider(3),new String[]{"sf-express.com"})||Arrays.asList(LogoutSites.forPickupProvider(4)).contains("jd.com"))throw new AssertionError("Targeted logout");
  try{LogoutSites.forPickupProvider(-1);throw new AssertionError("Unknown provider accepted");}catch(IllegalArgumentException expected){}
  System.out.println("PASS: summary provider navigation and logout isolation");
 }
}
