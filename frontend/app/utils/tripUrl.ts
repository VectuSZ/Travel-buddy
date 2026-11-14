import type {Trip} from '../../shared/trip'
export function tripUrl(trip:Pick<Trip,'id'|'slug'>,locale:string){return (locale==='en'?'/en/trips/':'/pl/wyprawy/')+trip.id+'/'+trip.slug}
