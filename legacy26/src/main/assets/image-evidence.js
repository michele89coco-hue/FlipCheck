/* Local image measurements. No network, model, catalogue or card-name lookup.
 * A comparison is supporting evidence, never an identity or authenticity verdict.
 */
(function(root){'use strict';
const median=a=>{const b=a.slice().sort((a,b)=>a-b);return b.length?b[Math.floor(b.length/2)]:0;};
function analyze(pixels,width,height){
 if(!Number.isInteger(width)||!Number.isInteger(height)||width<1||height<1||!pixels||pixels.length<width*height*4)return {state:'unclear',reason:'invalid_pixels',method:'outer_frame_contrast',paid_requests:0};
 const scale=Math.min(1,480/Math.max(width,height)),w=Math.round(width*scale),h=Math.round(height*scale),rgb=new Uint8Array(w*h*3),mask=new Uint8Array(w*h);
 const yellow=(r,g,b)=>r>105&&g>90&&r>=g*.98&&r<g*1.8&&g-b>23&&r-b>40;
 for(let y=0;y<h;y++)for(let x=0;x<w;x++){const s=(Math.min(height-1,Math.floor(y/scale))*width+Math.min(width-1,Math.floor(x/scale)))*4,i=y*w+x;for(let k=0;k<3;k++)rgb[i*3+k]=pixels[s+k];mask[i]=yellow(...rgb.subarray(i*3,i*3+3))?1:0;}
 const visited=new Uint8Array(w*h),queue=new Int32Array(w*h);let largest=[];
 for(let n=0;n<mask.length;n++)if(mask[n]&&!visited[n]){let head=0,tail=1;queue[0]=n;visited[n]=1;while(head<tail){const i=queue[head++],x=i%w;for(const j of [x?i-1:-1,x<w-1?i+1:-1,i-w,i+w])if(j>=0&&j<mask.length&&mask[j]&&!visited[j]){visited[j]=1;queue[tail++]=j;}}if(tail>largest.length)largest=Array.from(queue.subarray(0,tail));}
 const unknown=reason=>({state:'unclear',reason,method:'outer_frame_contrast',paid_requests:0});
 if(largest.length<w*h*.018)return unknown('frame_not_located');
 const ys=largest.map(i=>Math.floor(i/w)),xs=largest.map(i=>i%w),left=xs.reduce((a,b)=>Math.min(a,b),w),right=xs.reduce((a,b)=>Math.max(a,b),0),top=ys.reduce((a,b)=>Math.min(a,b),h),bottom=ys.reduce((a,b)=>Math.max(a,b),0),bw=right-left,bh=bottom-top;
 if(bw<90||bh<125||bw/bh<.52||bw/bh>.92||left<1||right>=w-2||top<1||bottom>=h-2)return unknown('frame_geometry_or_crop');
 const rows=new Map(),cols=new Map();for(const i of largest){const x=i%w,y=Math.floor(i/w),r=rows.get(y)||[x,x],c=cols.get(x)||[y,y];r[0]=Math.min(r[0],x);r[1]=Math.max(r[1],x);c[0]=Math.min(c[0],y);c[1]=Math.max(c[1],y);rows.set(y,r);cols.set(x,c);}
 const fit=points=>{const n=points.length,sx=points.reduce((s,p)=>s+p[0],0),sy=points.reduce((s,p)=>s+p[1],0),sxx=points.reduce((s,p)=>s+p[0]*p[0],0),sxy=points.reduce((s,p)=>s+p[0]*p[1],0);const m=(n*sxy-sx*sy)/(n*sxx-sx*sx);return [m,(sy-m*sx)/n];};
 const rowPoints=[...rows].filter(([y,r])=>y>top+bh*.2&&y<bottom-bh*.2&&r[1]-r[0]>bw*.75),colPoints=[...cols].filter(([x,c])=>x>left+bw*.2&&x<right-bw*.2&&c[1]-c[0]>bh*.75);
 if(rowPoints.length<30||colPoints.length<30)return unknown('incomplete_outer_frame');
 const L=fit(rowPoints.map(([y,r])=>[y,r[0]])),R=fit(rowPoints.map(([y,r])=>[y,r[1]])),T=fit(colPoints.map(([x,c])=>[x,c[0]])),B=fit(colPoints.map(([x,c])=>[x,c[1]]));
 const cross=(a,b)=>{const x=(a[0]*b[1]+a[1])/(1-a[0]*b[0]);return [x,b[0]*x+b[1]];},quad=[cross(L,T),cross(R,T),cross(R,B),cross(L,B)];
 const W=320,H=448,out=new Uint8Array(W*H*3),gold=new Uint8Array(W*H),luma=new Float32Array(W*H);
 for(let y=0;y<H;y++)for(let x=0;x<W;x++){const u=x/(W-1),v=y/(H-1),sx=((1-u)*(1-v)*quad[0][0]+u*(1-v)*quad[1][0]+u*v*quad[2][0]+(1-u)*v*quad[3][0])/scale,sy=((1-u)*(1-v)*quad[0][1]+u*(1-v)*quad[1][1]+u*v*quad[2][1]+(1-u)*v*quad[3][1])/scale,si=(Math.max(0,Math.min(height-1,Math.round(sy)))*width+Math.max(0,Math.min(width-1,Math.round(sx))))*4,i=y*W+x;for(let k=0;k<3;k++)out[i*3+k]=pixels[si+k];gold[i]=yellow(...out.subarray(i*3,i*3+3));luma[i]=out[i*3]*.2126+out[i*3+1]*.7152+out[i*3+2]*.0722;}
 // Find the long gold artwork rails inside the rectified yellow card frame.
 const rails=[];for(let y=H*.09|0;y<H*.56;y++){let start=-1,gaps=0;for(let x=W*.06|0;x<W*.95;x++){if(gold[y*W+x]){if(start<0)start=x;gaps=0;}else if(start>=0&&++gaps>2){if(x-start>W*.60)rails.push({y,left:start,right:x-gaps});start=-1;}}}
 const uppers=rails.filter(r=>r.y<H*.28),lowers=rails.filter(r=>r.y>H*.4);let pair;
 for(const a of uppers)for(const b of lowers)if(Math.abs(a.left-b.left)<W*.035&&Math.abs(a.right-b.right)<W*.035&&(!pair||b.y-a.y>pair[1].y-pair[0].y))pair=[a,b];
 if(!pair)return {...unknown('artwork_frame_not_located'),card_quad:quad.map(p=>({x:p[0]/w,y:p[1]/h}))};
 const [a,b]=pair,deltas=[],lower=[];
 for(let y=a.y+20;y<b.y-15;y+=3){const predicted=a.right+(b.right-a.right)*(y-a.y)/(b.y-a.y);let edge=-1;for(let x=predicted-7|0;x<=predicted+7;x++)if(gold[y*W+x])edge=x;if(edge<0||edge+18>=W)continue;const near=[],far=[];for(let x=edge+2;x<=edge+7;x++)near.push(luma[y*W+x]);for(let x=edge+12;x<=edge+18;x++)far.push(luma[y*W+x]);deltas.push(median(far)-median(near));}
 for(let x=a.left+14;x<b.right-8;x+=4){let edge=-1;for(let y=b.y-5;y<=b.y+3;y++)if(gold[y*W+x])edge=y;if(edge<0)continue;const near=[],far=[];for(let y=edge+2;y<=edge+5;y++)near.push(luma[y*W+x]);for(let y=edge+10;y<=edge+16;y++)far.push(luma[y*W+x]);lower.push(median(far)-median(near));}
 const edgeState=values=>{if(values.length<15)return {state:'unclear',samples:values.length};const mid=median(values),dark=values.filter(x=>x>22).length/values.length;return {state:mid>23&&dark>.72?'present':mid<10&&dark<.18?'absent':'unclear',median_contrast:Math.round(mid*10)/10,dark_fraction:Math.round(dark*100)/100,samples:values.length};};
 const rightEdge=edgeState(deltas),lowerEdge=edgeState(lower),state=rightEdge.state===lowerEdge.state?rightEdge.state:'unclear';
 return {state,method:'outer_frame_contrast',right:rightEdge,lower:lowerEdge,card_quad:quad.map(p=>({x:p[0]/w,y:p[1]/h})),artwork_box:{x:a.left/W,y:a.y/H,width:(b.right-a.left)/W,height:(b.y-a.y)/H},paid_requests:0};
}
async function pixels(data){const im=new Image();await new Promise((resolve,reject)=>{im.onload=resolve;im.onerror=reject;im.src=data;});const c=document.createElement('canvas');c.width=im.width;c.height=im.height;const g=c.getContext('2d',{willReadFrequently:true});g.drawImage(im,0,0);return {data:g.getImageData(0,0,c.width,c.height).data,width:c.width,height:c.height};}
function signature(p){const sample=[];for(let y=0;y<12;y++)for(let x=0;x<12;x++){const i=((Math.floor((y+.5)*p.height/12))*p.width+Math.floor((x+.5)*p.width/12))*4;sample.push((p.data[i]*.2126+p.data[i+1]*.7152+p.data[i+2]*.0722)/255);}const mean=sample.reduce((a,b)=>a+b,0)/sample.length;return {aspect:p.width/p.height,values:sample.map(v=>v-mean)};}
function compare(a,b){let distance=0;for(let i=0;i<a.values.length;i++)distance+=Math.abs(a.values[i]-b.values[i]);return {mean_pixel_distance:Math.round(distance/a.values.length*1000)/1000,aspect_ratio_difference:Math.round(Math.abs(Math.log(a.aspect/b.aspect))*1000)/1000,scope:'appearance_only',identity_proof:false};}
const api={analyze,pixels,signature,compare};if(typeof module!=='undefined'&&module.exports)module.exports=api;else root.FlipCheckImageEvidence=api;
})(typeof window!=='undefined'?window:globalThis);
