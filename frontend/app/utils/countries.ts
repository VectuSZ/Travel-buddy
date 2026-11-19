// A single display continent per ISO 3166-1 territory; see docs/DECISIONS.md.
const regions = {
  '002': 'AO BF BI BJ BW CD CF CG CI CM CV DJ DZ EG EH ER ET GA GH GM GN GQ GW IO KE KM LR LS LY MA MG ML MR MU MW MZ NA NE NG RE RW SC SD SH SL SN SO SS ST SZ TD TG TN TZ UG YT ZA ZM ZW',
  '019-north': 'AG AI AW BB BL BM BQ BS BZ CA CR CU CW DM DO GD GL GP GT HN HT JM KN KY LC MF MQ MS MX NI PA PM PR SV SX TC TT US VC VG VI',
  '005': 'AR BO BR CL CO EC FK GF GY PE PY SR UY VE',
  '142': 'AE AF AM AZ BD BH BN BT CN CY GE HK ID IL IN IQ IR JO JP KG KH KP KR KW KZ LA LB LK MM MN MO MV MY NP OM PH PK PS QA SA SG SY TH TJ TL TM TR TW UZ VN YE',
  '150': 'AD AL AT AX BA BE BG BY CH CZ DE DK EE ES FI FO FR GB GG GI GR HR HU IE IM IS IT JE LI LT LU LV MC MD ME MK MT NL NO PL PT RO RS RU SE SI SJ SK SM UA VA',
  '009': 'AS AU CC CK CX FJ FM GU HM KI MH MP NC NF NR NU NZ PF PG PN PW SB TK TO TV UM VU WF WS',
  'AQ': 'AQ BV GS TF',
}
const continentNames: Record<string,Record<string,string>> = {
  pl:{'002':'Afryka','019-north':'Ameryka Północna','005':'Ameryka Południowa','142':'Azja','150':'Europa','009':'Oceania',AQ:'Antarktyda'},
  en:{'002':'Africa','019-north':'North America','005':'South America','142':'Asia','150':'Europe','009':'Oceania',AQ:'Antarctica'},
}
const names = new Map<string,Intl.DisplayNames>()
function display(code:string, locale:string) {
  if (!names.has(locale)) names.set(locale,new Intl.DisplayNames([locale],{type:'region'}))
  return names.get(locale)!.of(code) || code
}
export const countryRegions = new Map(Object.entries(regions).flatMap(([region,codes]) =>
  codes.split(' ').map(code => [code,region] as const)))
export function countryName(code:string,locale:string) {
  return countryRegions.has(code) ? display(code,locale) : code
}
export function routeSummary(locations:{countryCode:string}[],locale:string) {
  const codes = [...new Set(locations.map(location => location.countryCode))]
  const continents = [...new Set(codes.map(code => countryRegions.get(code)))].filter(Boolean)
  return [codes.map(code => countryName(code,locale)).join(', '),
    continents.map(region => (continentNames[locale] || continentNames.en)![region!]).join(', ')
  ].filter(Boolean).join(' · ')
}
const aliases:Record<string,string[]> = {GB:['Anglia','England','UK','Great Britain','Wielka Brytania','United Kingdom']}
function normalize(text:string) {
  return text.toLowerCase().replaceAll('ł','l').normalize('NFD').replace(/\p{M}/gu,'').trim()
}
export function countryOptions(locale:string) {
  return [...countryRegions.keys()].map(code => ({
    code, name:countryName(code,locale),
    search:normalize([code,countryName(code,'pl'),countryName(code,'en'),...(aliases[code] || [])].join('|'))
  })).sort((a,b)=>a.name.localeCompare(b.name,locale))
}
export function matchesCountry(search:string,query:string) { return search.includes(normalize(query)) }
