import {test,expect} from '@playwright/test'
for(const lang of ['pl','en']){
 test('application acceptance, contacts and rejection '+lang,async({browser})=>{
  const prefix=lang==='en'?'/en':'',en=lang==='en',stamp=Date.now()
  const contexts=await Promise.all([browser.newContext({baseURL:process.env.E2E_BASE_URL||'http://localhost:3000'}),browser.newContext({baseURL:process.env.E2E_BASE_URL||'http://localhost:3000'}),browser.newContext({baseURL:process.env.E2E_BASE_URL||'http://localhost:3000'})])
  try{
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
   const trip=await (await write(0,'/api/v1/trips',{title:'Application trip '+stamp,itinerary:'Walk together',lookingFor:'Travelers',contentLanguage:'en',dateMode:'EXACT',dateFrom:'2027-07-01',dateTo:'2027-07-03',targetPeople:3,budgetMin:100,budgetMax:500,styles:['trekking'],locations:[{countryCode:'PL'}]})).json()
   expect((await write(0,'/api/v1/trips/'+trip.id+'/publish',{version:trip.version})).status()).toBe(200)
   const [a,b,c]=pages
   for(const p of [b!,c!]){
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
   const rejected=a!.locator('article').filter({hasText:'Participant 2'})
   await rejected.getByRole('button',{name:en?'Reject':'Odrzuć',exact:true}).click()
   await expect(rejected).toContainText(en?'Rejected':'Odrzucone')
   await b!.reload()
   await expect(b!.locator('main ul')).toContainText('Participant 1')
   await b!.getByRole('button',{name:en?'Show contact':'Pokaż kontakt',exact:true}).click()
   await expect(b!.locator('aside')).toContainText('private-'+stamp+'-0@example.test')
   const html=await (await b!.request.get(b!.url())).text()
   expect(html).toContain('Participant 1')
   expect(html).not.toContain('private-'+stamp)
   expect(html).not.toContain('Private message '+stamp)
   expect(html).not.toContain('application-'+stamp)
   await b!.getByRole('button',{name:en?'Withdraw application':'Wycofaj zgłoszenie',exact:true}).click()
   await expect(b!.locator('aside')).toHaveCount(0)
   await expect(b!.locator('main')).toContainText(en?'Withdrawn':'Wycofane')
   await c!.goto(prefix+'/my-applications')
   await expect(c!.locator('main')).toContainText(en?'Rejected':'Odrzucone')
  }finally{await Promise.all(contexts.map(c=>c.close()))}
 })
}
