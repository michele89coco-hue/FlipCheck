const {test}=require('node:test'),assert=require('node:assert/strict');
const E=require('../src/main/assets/catalogue-engine'),L=require('../src/main/assets/lens-policy');
const recorded=require('./fixtures/lens-209-first-match.json');
function fixture(){const f=structuredClone(recorded),l=new E.Ledger(f.vision);E.ingestVision(l,f.vision);L.reconcileOriginal208(l,f.reply.original_readings,f.crops);f.reference.image_data='downloaded-test-carrier';return {...f,l};}
test('210 recorded 209 comparison survives abbreviated quote without inventing release year',()=>{
 const f=fixture(),before=f.l.active('collector_number').map(a=>a.id),out=L.visualEntries206(f.reply,[f.reference],f.l);
 assert.equal(out.accepted.length,1,JSON.stringify(out.rejected));const entry=out.accepted[0];assert.equal(entry.year,'');assert.equal(entry.copyright_year,'2024');assert.equal(entry.field_proof.subject.origin,'reference_text');assert.equal(entry.field_proof.year,undefined);
 const result=E.reduce(f.l,out.accepted);assert.equal(result.core_identity.status,'confirmed');assert.equal(result.market_ready,true,JSON.stringify(result.missing_information));assert.equal(result.catalogue_release_year,null);assert.equal(result.printed_year,'2024');assert.deepEqual(f.l.active('collector_number').map(a=>a.id),before);
});
test('210 conflicting identifiers, invented names and partial name matches remain rejected',()=>{
 for(const mutate of [f=>f.reply.comparisons[0].reference_reading.number='136/127',f=>f.reply.comparisons[0].identity.subject='Mewthree V',f=>f.reference.title=f.reference.title.replace('Mewtwo V','Mewtwo VMAX'),f=>f.reply.comparisons[0].identity.family='Aquapolis',f=>f.reply.comparisons[0].reference_reading.year='2022']){
 const f=fixture();mutate(f);assert.equal(L.visualEntries206(f.reply,[f.reference],f.l).accepted.length,0);
 }
});
test('210 a missing unproven year does not block the other verified fields',()=>{const f=fixture();f.reply.comparisons[0].identity.year='';const out=L.visualEntries206(f.reply,[f.reference],f.l);assert.equal(out.accepted.length,1);assert.equal(out.accepted[0].year,'');});
test('210 a later generic language reading does not erase known Chinese script',()=>{const f=fixture();assert.equal(f.l.pick('language').value,'zh-hans');const row=f.reply.original_readings.find(r=>r.field==='language');row.full_text='Cinese';row.crop_text='Testo cinese';L.reconcileOriginal208(f.l,[row],f.crops);assert.equal(f.l.pick('language').value,'zh-hans');});

test('210 documented release year stays separate from matching image copyright',()=>{const f=fixture();f.reference.title+=' Released 2023';f.reply.comparisons[0].identity.year='2023';const out=L.visualEntries206(f.reply,[f.reference],f.l);assert.equal(out.accepted.length,1);const r=E.reduce(f.l,out.accepted);assert.equal(r.catalogue_release_year,'2023');assert.equal(r.printed_year,'2024');assert.equal(r.market_ready,true);});
