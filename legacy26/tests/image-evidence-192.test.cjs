'use strict';
const {test}=require('node:test'),assert=require('node:assert/strict');
const E=require('../src/main/assets/image-evidence.js');
function scene(shadow,brightness=1){
 const width=400,height=560,data=new Uint8ClampedArray(width*height*4);
 const rect=(x,y,w,h,c)=>{for(let yy=y;yy<y+h;yy++)for(let xx=x;xx<x+w;xx++){let i=(yy*width+xx)*4;for(let k=0;k<3;k++)data[i+k]=c[k]*brightness;data[i+3]=255;}};
 rect(0,0,400,560,[40,40,40]);rect(20,24,360,504,[230,190,45]);rect(34,38,332,476,[150,195,210]);
 if(shadow){rect(57,99,291,205,[65,80,90]);}
 rect(53,90,284,205,[208,177,82]);rect(59,96,272,193,[160,170,180]);
 return {data,width,height};
}
for(const shadow of [false,true])for(const light of [.85,1,1.1])test('192 two-edge pixel measurement '+shadow+' brightness '+light,()=>{
 const p=scene(shadow,light),r=E.analyze(p.data,p.width,p.height);assert.equal(r.state,shadow?'present':'absent',JSON.stringify(r));assert.equal(r.paid_requests,0);
});
test('192 blank, solid yellow, malformed and clipped photos stay unclear',()=>{
 for(const color of [[0,0,0],[230,190,45]]){const data=new Uint8Array(480*480*4);for(let i=0;i<data.length;i+=4)data.set([...color,255],i);assert.equal(E.analyze(data,480,480).state,'unclear');}
 assert.equal(E.analyze([],0,0).state,'unclear');const p=scene(false);assert.equal(E.analyze(p.data,400,700).state,'unclear');
});
test('192 local comparison is appearance evidence and never an identity verdict',()=>{
 const a=scene(false),b=scene(true),same=E.compare(E.signature(a),E.signature(a)),other=E.compare(E.signature(a),E.signature(b));
 assert.equal(same.mean_pixel_distance,0);assert.ok(other.mean_pixel_distance>0);assert.equal(other.identity_proof,false);
});
