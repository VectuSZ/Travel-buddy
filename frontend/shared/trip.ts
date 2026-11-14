export interface Trip { slug:string; status:string; id:string; title:string; country:string; description:string; startDate:string; endDate:string; contentLanguage:string; dateMode:string; budgetMin:number;budgetMax:number;locations:{countryCode:string;city:string|null}[] }
export interface TripResults {items:Trip[];total:number;page:number;size:number}
