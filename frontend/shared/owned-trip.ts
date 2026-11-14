export interface TripFields {
 title:string|null; itinerary:string|null; lookingFor:string|null; contentLanguage:string|null
 dateMode:string|null; dateFrom:string|null; dateTo:string|null
 durationDays:number|null; targetPeople:number|null; budgetMin:number|null; budgetMax:number|null
 styles:string[]; locations:{countryCode:string;city:string|null}[]
}
export interface OwnedTrip extends TripFields { id:string;version:number;status:'DRAFT'|'OPEN'|'CLOSED' }
