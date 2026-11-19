import {test,expect} from '@playwright/test'
for (const locale of ['pl','en']) test(`account deletion ${locale}`,async({page,context})=>{
 const base=process.env.E2E_BASE_URL||'http://localhost:3000'
 const password='Deletion fixture password 123!',email=`delete-${locale}-${Date.now()}@example.test`
 async function write(url:string,data:object,method='POST') {
  const csrf=await (await context.request.get('/api/v1/auth/csrf')).json()
  return context.request.fetch(url,{method,data,headers:{Origin:base,'X-CSRF-TOKEN':csrf.token}})
 }
 expect((await write('/api/v1/auth/register',{email,password,passwordConfirmation:password,locale,acceptTerms:true,termsVersion:'local-draft-2'})).status()).toBe(201)
 expect((await write('/api/v1/me/profile',{version:0,displayName:'Deletion fixture',age:30,languages:['pl'],travelStyles:['trekking']},'PATCH')).status()).toBe(200)
 expect((await write('/api/v1/me/contacts',{contactEmail:email},'PUT')).status()).toBe(200)
 const user=await (await context.request.get('/api/v1/me')).json()
 const trip=await (await write('/api/v1/trips',{title:'Deletion fixture',itinerary:'Travel plan',lookingFor:'People',contentLanguage:locale,dateMode:'EXACT',dateFrom:'2027-08-01',dateTo:'2027-08-03',targetPeople:4,budgetMin:100,budgetMax:500,styles:['trekking'],locations:[{countryCode:'PL'}]})).json()
 expect((await write(`/api/v1/trips/${trip.id}/publish`,{version:trip.version})).status()).toBe(200)
 await page.goto(locale==='en'?'/en/account':'/account')
 await page.getByLabel(locale==='pl'?'Potwierdź aktualne hasło':'Confirm current password').fill(password)
 await page.getByLabel(locale==='pl'?'Potwierdzam trwałe usunięcie danych':'I confirm permanent deletion of my data').check()
 await page.getByRole('button',{name:locale==='pl'?'Usuń konto':'Delete account',exact:true}).click()
 await expect(page).not.toHaveURL(/account/)
 expect((await context.request.get('/api/v1/auth/session')).status()).toBe(401)
 expect((await context.request.get(`/api/v1/profiles/${user.userId}`)).status()).toBe(404)
 expect((await context.request.get(`/api/v1/trips/${trip.id}`)).status()).toBe(404)
})
