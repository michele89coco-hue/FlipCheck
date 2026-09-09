const {test}=require('node:test'),assert=require('node:assert/strict'),L=require('../src/main/assets/lens-policy');
const ready={status:200,body:{enabled:true,protocol:2,provider:'searchapi_google_lens'}};
function clock(){let time=0;return {now:()=>time,wait:async ms=>{time+=ms;},advance:ms=>{time+=ms;}};}
test('recorded 12.2-second Render startup is allowed and returns configuration',async()=>{
 const c=clock();let calls=0;const result=await L.waitForService207(async timeout=>{calls++;assert.ok(timeout>12223);c.advance(12223);return ready;},c);assert.equal(result,ready);assert.equal(calls,1);
});
test('a minute-long startup retries configuration only and succeeds within total bound',async()=>{
 const c=clock();let calls=0;const result=await L.waitForService207(async timeout=>{calls++;c.advance(calls<3?timeout:10000);return calls<3?{status:0,state:'timeout'}:ready;},c);assert.equal(result,ready);assert.equal(calls,3);assert.equal(c.now(),70000);
});
test('persistent timeouts and fast temporary 503 failures stop at 80 seconds',async()=>{
 for(const status of [0,503]){const c=clock();let calls=0;const r=await L.waitForService207(async timeout=>{calls++;if(!status)c.advance(timeout);return {status,state:'timeout'};},c);assert.equal(r.state,'service_startup_timeout');assert.ok(c.now()<=80000);assert.ok(calls<=16);}
});
test('authentication errors, missing key and quota errors are not retried',async()=>{
 for(const reply of [{status:401,body:{state:'unauthorized'}},{status:403},{status:429},{status:200,body:{enabled:false,state:'not_configured'}}]){let calls=0;assert.equal(await L.waitForService207(async()=>{calls++;return reply;},clock()),reply);assert.equal(calls,1);}
});
test('cancellation during warmup prevents any following request',async()=>{
 const c=clock();let cancelled=false,calls=0;await assert.rejects(L.waitForService207(async()=>{calls++;cancelled=true;return {status:503};},{...c,guard(){if(cancelled)throw new Error('scan_cancelled');}}),/scan_cancelled/);assert.equal(calls,1);
});
test('transport rejection can recover, unrelated failures remain errors',async()=>{
 let calls=0;assert.equal(await L.waitForService207(async()=>{if(++calls===1)throw new Error('network_error');return ready;},clock()),ready);
 await assert.rejects(L.waitForService207(async()=>{throw new Error('invalid_request');},clock()),/invalid_request/);
});
