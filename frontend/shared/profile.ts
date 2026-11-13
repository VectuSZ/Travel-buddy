export interface PublicProfile { userId:string; displayName:string; age:number; bio:string|null; languages:string[]; travelStyles:string[] }
export interface OwnProfile extends Omit<PublicProfile,'userId'> { version:number; ageUpdatedAt:string }
export interface Contacts { contactEmail:string|null; instagramHandle:string|null; whatsappE164:string|null }
export interface Me { userId:string; email:string; profile:OwnProfile|null; contacts:Contacts; profileComplete:boolean; hasContact:boolean; readyToParticipate:boolean }
export const languageCodes = ['pl','en','de','fr','es','it','pt','uk','cs','sk','nl','sv','no','da','fi','el','tr','ar','zh','ja','ko','hi']
export const styleCodes = ['backpacking','comfort','trekking','city_break']
