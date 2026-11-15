import {test,expect} from '@playwright/test'
import {execFileSync} from 'node:child_process'
test('report by A, ADMIN hides trip, anonymous 404',async({browser})=>{
 test.skip(process.env.E2E_SETUP_ADMIN!=='1','Requires explicit local test admin setup')
 const base=process.env.E2E_BASE_URL||'http://localhost:3000',stamp=Date.now()
 let adminId=''
 const contexts=await Promise.all([browser.newContext({baseURL:base}),browser.newContext({baseURL:base}),browser.newContext({baseURL:base})])
 try{
  async function write(i:number,url:string,data:object,method='POST'){
   const r=contexts[i]!.request,csrf=await (await r.get('/api/v1/auth/csrf')).json()
   return r.fetch(url,{method,data,headers:{Origin:base,'X-CSRF-TOKEN':csrf.token}})
  }
  for(let i=0;i<2;i++){
   expect((await write(i,'/api/v1/auth/register',{email:'mod-'+stamp+'-'+i+'@example.test',password:'Long moderation test password 123!',passwordConfirmation:'Long moderation test password 123!',locale:'pl',acceptTerms:true,termsVersion:'local-draft-1'})).status()).toBe(201)
   await write(i,'/api/v1/me/profile',{version:0,displayName:'Moderation tester '+i,age:30,languages:['pl'],travelStyles:['trekking']},'PATCH')
   await write(i,'/api/v1/me/contacts',{contactEmail:'secret-mod-'+stamp+'@example.test'},'PUT')
  }
  const admin=await (await contexts[1]!.request.get('/api/v1/me')).json()
  adminId=admin.userId
  expect(admin.userId).toMatch(/^[0-9a-f-]{36}$/)
  // Promote only this freshly registered fixture, never an existing account.
  execFileSync('docker',['compose','exec','-T','postgres','sh','-c','psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"'],{cwd:'..',input:"UPDATE users SET role='ADMIN' WHERE id='"+admin.userId+"' AND email='mod-"+stamp+"-1@example.test';",stdio:['pipe','pipe','pipe']})
  const trip=await (await write(0,'/api/v1/trips',{title:'Reported trip '+stamp,itinerary:'Travel plan',lookingFor:'People',contentLanguage:'pl',dateMode:'EXACT',dateFrom:'2027-08-01',dateTo:'2027-08-03',targetPeople:4,budgetMin:100,budgetMax:500,styles:['trekking'],locations:[{countryCode:'PL'}]})).json()
  expect((await write(0,'/api/v1/trips/'+trip.id+'/publish',{version:trip.version})).status()).toBe(200)
  const a=await contexts[0]!.newPage(),moderator=await contexts[1]!.newPage(),anonymous=await contexts[2]!.newPage()
  await a.goto('/trips/'+trip.id)
  await a.getByRole('link',{name:'Zgłoś',exact:true}).click()
  await a.locator('[name=category]').selectOption('SPAM')
  await a.locator('[name=reportDescription]').fill('Report description '+stamp)
  await a.locator('[name=goodFaith]').check()
  await a.getByRole('button',{name:'Weryfikuj lokalnie'}).click()
  await a.getByRole('button',{name:'Wyślij zgłoszenie'}).click()
  await expect(a.getByRole('status')).toContainText('Zgłoszenie przyjęto')
  const denied=await contexts[0]!.request.get('/api/v1/admin/reports')
  expect(denied.status()).toBe(403)
  await moderator.goto('/moderation')
  const report=moderator.locator('article').filter({hasText:'Report description '+stamp})
  await expect(report).toBeVisible()
  await report.locator('[name=reason]').fill('Reviewed: spam content')
  await report.getByRole('button',{name:'Ukryj wyprawę'}).click()
  await expect(moderator.getByRole('status')).toContainText('Zapisano')
  await report.getByRole('button',{name:'Rozstrzygnij',exact:true}).click()
  await expect(report).toHaveCount(0)
  await anonymous.goto('/pl/wyprawy?country=PL')
  await expect(anonymous.locator('main')).not.toContainText('Reported trip '+stamp)
  const response=await anonymous.goto('/trips/'+trip.id)
  expect(response!.status()).toBe(404)
  const html=await response!.text()
  expect(html).not.toContain('secret-mod-'+stamp)
  expect(html).not.toContain('Report description '+stamp)
  const owner=await (await contexts[0]!.request.get('/api/v1/me')).json()
  await anonymous.goto('/en/report?user='+owner.userId)
  await anonymous.locator('[name=reporterName]').fill('Anonymous tester')
  await anonymous.locator('[name=reporterEmail]').fill('anonymous-'+stamp+'@example.test')
  await anonymous.locator('[name=category]').selectOption('OTHER')
  await anonymous.locator('[name=reportDescription]').fill('Anonymous description '+stamp)
  await anonymous.locator('[name=goodFaith]').check()
  await anonymous.getByRole('button',{name:'Verify locally'}).click()
  await anonymous.route('**/api/v1/reports',async route=>{
   const payload=route.request().postDataJSON();payload.token='invalid'
   await route.continue({postData:JSON.stringify(payload)})
  })
  await anonymous.getByRole('button',{name:'Submit report'}).click()
  await expect(anonymous.getByRole('alert')).toContainText('Verification invalid')
  await expect(anonymous.locator('[name=reportDescription]')).toHaveValue('Anonymous description '+stamp)
  await anonymous.unroute('**/api/v1/reports')
  await anonymous.getByRole('button',{name:'Verify locally'}).click()
  await anonymous.getByRole('button',{name:'Submit report'}).click()
  await expect(anonymous.getByRole('status')).toContainText('Your report was received')
  await moderator.goto('/en/moderation')
  await expect(moderator.getByRole('heading',{name:'Moderation',exact:true})).toBeVisible()
 }finally{
  if(/^[0-9a-f-]{36}$/.test(adminId))execFileSync('docker',['compose','exec','-T','postgres','sh','-c','psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"'],{cwd:'..',input:"UPDATE users SET role='USER' WHERE id='"+adminId+"' AND email='mod-"+stamp+"-1@example.test';",stdio:['pipe','pipe','pipe']})
  await Promise.all(contexts.map(c=>c.close()))
 }
})
