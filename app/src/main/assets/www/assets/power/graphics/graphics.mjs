/** Pure SVG graphics adapter. No timers, DOM access, vehicle bus, or production wiring. */
export const TOKENS=Object.freeze({steel:'#858e96',steelEdge:'#b4bdc5',empty:'#35404a',
  charge:'#39ce91',drive:'#22c9ff',regen:'#53ed91',engine:'#ffb65c',
  ribbonWidth:34,ribbonOpacity:0.42,glowWidth:48,glowOpacity:0.24,glowBlur:8,
  arrowSpacing:48,arrowSize:12,arrowOpacity:0.94});
const clamp=(v,a,b)=>Math.max(a,Math.min(b,v));
export function normalizeState(v,s={}){
  const flowFresh=s.flowFresh!==false,dir=n=>[-1,0,1].includes(n)?n:0;
  return {soc:s.socFresh!==false&&typeof s.soc==='number'&&Number.isFinite(s.soc)&&s.soc>=0&&s.soc<=100?s.soc:null,
    front:flowFresh?dir(s.front):0,rear:flowFresh&&v.awd?dir(s.rear):0,
    engineActive:flowFresh&&s.engineActive===true,
    // Engine-on does NOT imply engine-to-wheel or engine-to-battery power.
    engineToFront:flowFresh&&s.engineToFront===true,
    charging:flowFresh&&v.key!=='hev2'&&s.charging===true};
}
export function moduleLevels(soc,count){
  const known=typeof soc==='number'&&Number.isFinite(soc)&&soc>=0&&soc<=100;
  return Array.from({length:count},(_,i)=>known?clamp(soc/100*count-i,0,1):0);
}
export function activeRoutes(v,state){
  const s=normalizeState(v,state),out=[];
  for(const [id,route] of Object.entries(v.routes)){
    const direction=id==='engine'?(s.engineToFront?1:0):id.startsWith('rear')?s.rear:s.front;
    if(direction)out.push({id,route,direction,color:id==='engine'?TOKENS.engine:direction<0?TOKENS.regen:TOKENS.drive});
  }return out;
}
export function sampleRoute(points,distance){
  const segments=points.slice(1).map((p,i)=>({a:points[i],b:p,length:Math.hypot(p[0]-points[i][0],p[1]-points[i][1])})).filter(s=>s.length>0);
  const total=segments.reduce((n,s)=>n+s.length,0);
  if(!total)return {x:points[0][0],y:points[0][1],angle:0,total:0};
  let d=clamp(distance,0,total);
  for(let i=0;i<segments.length;i++){const s=segments[i];if(d<=s.length||i===segments.length-1){const t=d/s.length;return {x:s.a[0]+(s.b[0]-s.a[0])*t,y:s.a[1]+(s.b[1]-s.a[1])*t,angle:Math.atan2(s.b[1]-s.a[1],s.b[0]-s.a[0])*180/Math.PI,total};}d-=s.length;}
}
const rect=(r,extra='')=>`<rect x="${r.x}" y="${r.y}" width="${r.width}" height="${r.height}" ${extra}/>`;
const pathData=points=>points.map((p,i)=>(i?'L':'M')+p.join(' ')).join(' ');
const esc=s=>String(s).replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&apos;'}[c]));
export function overlaySVG(v,state={},options={}){
  const s=normalizeState(v,state),prefix=options.idPrefix||`power-${v.key}`;
  if(!/^[a-zA-Z][\w-]*$/.test(prefix))throw Error('idPrefix must be a unique safe SVG identifier');
  const b=v.battery,small=b.height<140,gap=5,pad=10,labelH=small?28:42;
  const cellW=b.width-pad*2,cellH=(b.height-pad*2-labelH-(b.moduleCount-1)*gap)/b.moduleCount;
  const levels=moduleLevels(s.soc,b.moduleCount),cells=levels.map((level,i)=>({x:b.x+pad,y:b.y+pad+i*(cellH+gap),width:cellW,height:cellH,level}));
  let defs=`<filter id="${prefix}-glow" x="-100%" y="-100%" width="300%" height="300%"><feGaussianBlur stdDeviation="${TOKENS.glowBlur}"/></filter>`;
  for(const [name,route] of Object.entries(v.routes))defs+=`<path id="${prefix}-route-${name}" d="${pathData(route.points)}"/>`;
  defs+=`<clipPath id="${prefix}-battery-mask">${rect(b,'rx="12"')}</clipPath>`;
  cells.forEach((c,i)=>defs+=`<clipPath id="${prefix}-cell-${i}-mask">${rect(c,'rx="3"')}</clipPath>`);
  for(const [name,r] of Object.entries(v.highlights))defs+=`<clipPath id="${prefix}-${name}-mask">${rect(r,'rx="8"')}</clipPath>`;
  let content=`<g id="${prefix}-battery-outline" data-layer="battery-outline">${rect(b,`rx="12" fill="${TOKENS.steel}" stroke="${TOKENS.steelEdge}" stroke-width="3"`)}</g>`;
  content+=`<g data-layer="battery-cells" id="${prefix}-battery-cells" data-soc-known="${s.soc!==null}">`;
  cells.forEach((c,i)=>{
    content+=`<g data-module="${i}" data-level="${c.level}">${rect(c,`rx="3" fill="${TOKENS.empty}"`)}<g clip-path="url(#${prefix}-cell-${i}-mask)">${rect({...c,width:c.width*c.level},`data-fill="${i}" fill="${TOKENS.charge}" fill-opacity="0.74"`)}</g>`;
    for(let j=1;j<4;j++)content+=`<path d="M ${c.x+c.width*j/4} ${c.y} v ${c.height}" stroke="#1b2931" stroke-width="2"/>`;
    content+='</g>';
  });content+='</g>';
  content+=`<g data-layer="component-highlights" id="${prefix}-component-highlights">`;
  for(const [name,r] of Object.entries(v.highlights)){
    const direction=name==='frontMotor'?s.front:name==='rearMotor'?s.rear:s.engineActive?1:0;
    if(direction){const color=name==='engine'?TOKENS.engine:direction<0?TOKENS.regen:TOKENS.drive;
      content+=rect(r,`clip-path="url(#${prefix}-${name}-mask)" rx="8" fill="${color}" fill-opacity="0.24" stroke="${color}" stroke-width="3"`);}
  }content+='</g>';
  if(s.charging)content+=rect(b,`data-layer="charging-highlight" rx="12" fill="none" stroke="${TOKENS.regen}" stroke-width="5"`);
  content+=`<g data-layer="flows" id="${prefix}-flows" fill="none" stroke-linecap="round" stroke-linejoin="round">`;
  for(const {id,route,direction,color} of activeRoutes(v,s)){
    const d=pathData(route.points);
    content+=`<g data-route="${id}" data-direction="${direction}"><path d="${d}" stroke="${color}" stroke-width="${TOKENS.glowWidth}" opacity="${TOKENS.glowOpacity}" filter="url(#${prefix}-glow)"/><path data-ribbon="${id}" d="${d}" stroke="${color}" stroke-width="${TOKENS.ribbonWidth}" opacity="${TOKENS.ribbonOpacity}"/>`;
    const points=direction===-1?[...route.points].reverse():route.points;
    const total=sampleRoute(points,0).total;
    const phase=Number.isFinite(options.phase)?((options.phase%1)+1)%1:0;
    for(let n=16+phase*TOKENS.arrowSpacing;n<total-8;n+=TOKENS.arrowSpacing){const p=sampleRoute(points,n),a=TOKENS.arrowSize;
      content+=`<path data-chevron="${id}" d="M ${-a} ${-a} L 0 0 L ${-a} ${a}" transform="translate(${p.x} ${p.y}) rotate(${p.angle})" stroke="${color}" opacity="${TOKENS.arrowOpacity}" stroke-width="7"/>`;}
    content+='</g>';
  }content+='</g>';
  // Last layer keeps text readable over charge and flow glow, including at 0%.
  const label=s.soc===null?'—':`${Math.round(s.soc)}%`,ly=b.y+b.height-labelH/2-3;
  if(options.showSocLabel!==false)content+=`<g data-layer="soc-label" id="${prefix}-soc-label"><rect x="${b.x+b.width/2-43}" y="${ly-18}" width="86" height="32" rx="8" fill="#15232e" fill-opacity="0.94"/><text x="${b.x+b.width/2}" y="${ly+5}" text-anchor="middle" fill="#f5faff" font-family="sans-serif" font-size="${small?22:27}" font-weight="600">${label}</text></g>`;
  return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 600 1000" role="img" aria-labelledby="${prefix}-title" style="overflow:visible"><title id="${prefix}-title">${esc(v.key.toUpperCase())} battery ${s.soc===null?'unavailable':Math.round(s.soc)+' percent'}; front ${s.front===1?'drive':s.front===-1?'regeneration':'inactive'}; rear ${!v.awd?'not driven':s.rear===1?'drive':s.rear===-1?'regeneration':'inactive'}</title><defs>${defs}</defs>${content}</svg>`;
}
