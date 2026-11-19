import { test,expect } from '@playwright/test'
test('SSR documents, language links, cookies and public sitemap',async({request,browser})=>{
 const base=process.env.E2E_BASE_URL||'http://localhost:3000'
 const context=await browser.newContext({baseURL:base,javaScriptEnabled:false,viewport:{width:375,height:812}})
 try {
  const page=await context.newPage()
  const map=await (await request.get('/sitemap.xml')).text()
  expect(map).toContain('<urlset')
  expect(map).not.toMatch(/\/profiles\/|\/account|\?country=|\/moderation/)
  expect(await (await request.get('/robots.txt')).text()).toContain('Allow: /')
  for(const prefix of ['', '/en']){
   for(const path of ['/terms','/privacy','/cookies']){
    const url=prefix+path
    expect((await page.goto(url))?.status()).toBe(200)
    await expect(page.locator('h1')).toBeVisible()
    expect(await page.title()).toContain(await page.locator('h1').innerText())
    await expect(page.locator('link[rel=canonical]')).toHaveAttribute('href',base+url)
    await expect(page.locator('link[hreflang=en]')).toHaveAttribute('href',base+'/en'+path)
    await expect(page.locator('meta[name=description]')).toHaveAttribute('content',/.+/)
    expect(map).toContain('<loc>'+base+url+'</loc>')
    expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true)
   }
   await page.goto(prefix+'/register')
   await expect(page.locator('main a[href="'+prefix+'/privacy"]')).toBeVisible()
   await expect(page.locator('main')).toContainText('local-draft-2')
  }
 }finally{await context.close()}
})
