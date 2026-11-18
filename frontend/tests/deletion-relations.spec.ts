import {test,expect} from '@playwright/test'
for(const lang of ['pl']){
 test('participant and organizer deletion '+lang,async({browser})=>{
  const prefix=lang==='en'?'/en':'',en=lang==='en',stamp=Date.now()
  const contexts=await Promise.all([browser.newContext({baseURL:process.env.E2E_BASE_URL||'http://localhost:3000'}),browser.newContext({baseURL:process.env.E2E_BASE_URL||'http://localhost:3000'}),browser.newContext({baseURL:process.env.E2E_BASE_URL||'http://localhost:3000'})])
  try{
   const base=process.env.E2E_BASE_URL||'http://localhost:3000'
   contexts.push(await browser.newContext({baseURL:base}),await browser.newContext({baseURL:base}))
   const pages=await Promise.all(contexts.map(c=>c.newPage()))
   const write=async(index:number,url:string,data:object,method='POST')=>{
    const request=contexts[index]!.request
    const csrf=await (await request.get('/api/v1/auth/csrf')).json()
    return request.fetch(url,{method,data,headers:{Origin:process.env.E2E_BASE_URL||'http://localhost:3000','X-CSRF-TOKEN':csrf.token}})
   }
   for(let i=0;i<3;i++){
    const register=await write(i,'/api/v1/auth/register',{email:'application-'+stamp+'-'+i+'@example.test',password:'Long application password 123!',passwordConfirmation:'Long application password 123!',locale:lang,acceptTerms:true,termsVersion:'local-draft-1'})
    expect(register.status()).toBe(201)
    expect((await write(i,'/api/v1/me/profile',{version:0,displayName:'Participant '+i,age:30,languages:['pl'],travelStyles:['trekking']},'PATCH')).status()).toBe(200)
    await write(i,'/api/v1/me/contacts',{contactEmail:'private-'+stamp+'-'+i+'@example.test'},'PUT')
   }
   const trip=await (await write(0,'/api/v1/trips',{title:'Application trip '+stamp,itinerary:'Walk together',lookingFor:'Travelers',contentLanguage:'en',dateMode:'EXACT',dateFrom:'2027-07-01',dateTo:'2027-07-03',targetPeople:2,budgetMin:100,budgetMax:500,styles:['trekking'],locations:[{countryCode:'PL'}]})).json()
   expect((await write(0,'/api/v1/trips/'+trip.id+'/publish',{version:trip.version})).status()).toBe(200)
   const [a,b,c]=pages
   { const p=b!
    await p.goto(prefix+'/trips/'+trip.id)
    await p.locator('[name=applicationMessage]').fill('Private message '+stamp)
    await p.locator('[name=applicationConsent]').check()
    await p.getByRole('button',{name:en?'Apply':'Wyślij zgłoszenie',exact:true}).click()
    await expect(p.locator('main')).toContainText(en?'Pending':'Oczekujące')
   }
   await a!.goto(prefix+'/my-trips/edit?id='+trip.id)
   const incoming=a!.locator('article').filter({hasText:'Participant 1'})
   await expect(incoming).toContainText('Private message '+stamp)
   a!.once('dialog',d=>d.accept())
   await incoming.getByRole('button',{name:en?'Accept':'Akceptuj',exact:true}).click()
   await expect(incoming).toContainText(en?'Accepted':'Zaakceptowane')
   await incoming.getByRole('button',{name:en?'Show contact':'Pokaż kontakt',exact:true}).click()
   await expect(a!.locator('aside')).toContainText('private-'+stamp+'-1@example.test')

   await b!.reload()
   await b!.getByRole('button',{name:'Pokaż kontakt',exact:true}).click()
   await expect(b!.locator('aside')).toContainText('private-'+stamp+'-0@example.test')
   const second=pages[3]!, anon=pages[4]!
   const password='Long application password 123!'
   expect((await write(3,'/api/v1/auth/login',{email:'application-'+stamp+'-1@example.test',password})).status()).toBe(204)
   const ids=await Promise.all([a!,b!,c!].map(async p=>(await (await p.request.get('/api/v1/me')).json()).userId))
   const tripUrl=b!.url()
   const profiles=ids.map(id=>base+'/profiles/'+id)
   console.log(JSON.stringify({profiles,tripUrl,tripId:trip.id}))
   async function submit(page:typeof second, pass:string) {
    await page.goto('/account')
    await page.getByLabel('Potwierdź aktualne hasło').fill(pass)
    await page.getByLabel('Potwierdzam trwałe usunięcie danych').check()
    await page.getByRole('button',{name:'Usuń konto',exact:true}).click()
   }
   await submit(b!,'Incorrect password')
   await expect(b!.getByRole('alert').first()).toBeVisible()
   for(const p of [b!,second]) expect((await p.request.get('/api/v1/auth/session')).status()).toBe(200)
   await submit(b!,password)
   await expect(b!).not.toHaveURL(/account/)
   for(const p of [b!,second]) expect((await p.request.get('/api/v1/auth/session')).status()).toBe(401)
   expect((await anon.request.get(profiles[1]!)).status()).toBe(404)
   await anon.goto(tripUrl)
   await expect(anon.locator('main')).not.toContainText('Participant 1')
   expect((await a!.request.get('/api/v1/trips/'+trip.id+'/contacts/'+ids[1])).status()).toBe(404)
   await c!.goto(tripUrl)
   await c!.locator('[name=applicationMessage]').fill('Trial C '+stamp)
   await c!.locator('[name=applicationConsent]').check()
   await c!.getByRole('button',{name:'Wyślij zgłoszenie',exact:true}).click()
   await expect(c!.locator('main')).toContainText('Oczekujące')
   await submit(a!,password)
   await expect(a!).not.toHaveURL(/account/)
   await anon.goto('/pl/wyprawy')
   await expect(anon.locator('main')).not.toContainText('Application trip '+stamp)
   for(const url of [tripUrl,profiles[0]!]) expect((await anon.request.get(url)).status()).toBe(404)
   expect((await c!.request.get('/api/v1/auth/session')).status()).toBe(200)
   await c!.goto('/my-applications')
   await expect.poll(async()=> (await (await c!.request.get('/api/v1/me/applications')).json()).length,{timeout:20000}).toBe(0)
   await c!.reload()
   await expect(c!.locator('main')).not.toContainText('Application trip '+stamp)
  }finally{await Promise.all(contexts.map(c=>c.close()))}
 })
}
