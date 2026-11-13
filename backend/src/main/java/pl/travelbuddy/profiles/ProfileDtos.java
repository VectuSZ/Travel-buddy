package pl.travelbuddy.profiles;
import java.util.UUID;
import java.time.LocalDate;
public final class ProfileDtos {
 public record PublicProfile(UUID userId,String displayName,int age,String bio,String[] languages,String[] travelStyles) {}
 public record OwnProfile(String displayName,int age,LocalDate ageUpdatedAt,String bio,String[] languages,String[] travelStyles,int version) {}
 public record Contacts(String contactEmail,String instagramHandle,String whatsappE164) {}
 public record Me(UUID userId,String email,OwnProfile profile,Contacts contacts,boolean profileComplete,boolean hasContact,boolean readyToParticipate) {}
 public static PublicProfile publicDto(TravelerProfile p) { return new PublicProfile(p.userId,p.displayName,p.age,p.bio,p.languages,p.travelStyles); }
 public static OwnProfile own(TravelerProfile p) { return p==null?null:new OwnProfile(p.displayName,p.age,p.ageUpdatedAt,p.bio,p.languages,p.travelStyles,p.version); }
 public static Contacts contacts(ProfileContacts c) { return c==null?new Contacts(null,null,null):new Contacts(c.contactEmail,c.instagramHandle,c.whatsappE164); }
}
