/* Entirely synthetic public regression data. No uploaded photos, diagnostics or provider responses. */
const clue=(text,role='text')=>({text,role,certainty:'clear',image_index:1,region:null});
const reading=changes=>({status:'uncertain',kind:'object',title:'Example object',category:'object',object_unit:'object',brand:'',family:'',model:'',variant:'',model_confidence:20,category_confidence:99,brand_confidence:0,market_ready:false,normalized_query:'',candidate_models:[],missing_information:[],photo_clues:[],physical_observations:[],object_region:{image_index:1,x:0,y:0,width:1,height:1,certain:true},object_regions:[],next_photo_request:null,...changes});
const field=(reference_id,field,value,evidence='text',scope='target')=>({reference_id,field,value,quote:value,evidence,scope,number_kind:field==='year'?'year':field==='issue_number'?'issue_number':field==='catalog_number'?'card_number':'none'});
const match=(reference_id,feature,detail)=>({reference_id,feature,photo_detail:detail,reference_detail:detail,reference_evidence:'image',agrees:true});
const candidate=changes=>({category:'object',brand:'',family:'',model:'',year:'',issue_number:'',catalog_number:'',unit:'object',variant:'',identity_level:'exact',specimen_notes:[],decision:'match',same_unit:true,physical_ambiguity:false,conflicts:[],matches:[],fields:[],...changes});
const reference=(id,text,discovery=false)=>({id,url:'https://catalog.example/'+id,title:text,text,text_origin:discovery?'unattributed_image':'retrieved_page',discovery_only:discovery,image_url:'https://catalog.example/'+id+'.jpg',ocr:{state:'ok',text}});

const printing={is_pokemon:true,language:'English',set_name:'Base Set',card_type:'pokemon',first_edition_stamp:'present',stamp_image:1,stamp_location:'left below artwork',stamp_text:'1st Edition',artwork_shadow:'unclear',shadow_image:1,shadow_location:'right artwork border',copyright_text:'©1995,96,98,99 Nintendo ©1999 Wizards',copyright_image:1,slab_text:'',slab_image:0};
const printingFields=[field('ref1','model','Examplemon 9/102'),field('ref1','family','Pokemon Game Base Set'),field('ref1','catalog_number','9/102'),field('ref1','variant','1st Edition (Shadowed)')];
const machamp={
 vision:reading({kind:'card',category:'Pokémon TCG card',object_unit:'single',title:'Examplemon',model:'Examplemon 9/102',family:'Base Set',brand:'Pokémon',model_confidence:95,photo_clues:[clue('Examplemon'),clue('9/102','collector_number')],identity_basis:{family:'inferred',variant:'physical_evidence'},unresolved_identity_fields:['family'],pokemon_printing:printing,printing_detail_regions:[{detail:'copyright',region:{image_index:1,x:.2,y:.94,width:.7,height:.05,certain:true}}]}),
 identification:{kind:'card',family:'Pokemon Game Base Set',model:'Examplemon 9/102',variant:'Holo 1st Edition',market_ready:true,normalized_query:'Examplemon 9/102 Shadowed',model_confidence:95,catalogue_verified:true,catalogue_data:printingFields,variant_check:'pending',core_identity:{status:'partial',model:'Examplemon 9/102'}},
 candidates:[candidate({category:'Pokémon TCG card',unit:'single',family:'Pokemon Game Base Set',model:'Examplemon 9/102',variant:'1st Edition (Shadowed)',fields:printingFields,matches:[match('ref1','layout','matching illustration layout'),match('ref1','code','9/102')]})],
 references:[reference('ref1','Examplemon 9/102. Pokemon Game Base Set. 1st Edition (Shadowed).')]
};
const subject='Uncut collector photo of Alex Alpha and Casey Delta';
const panel={
 expected:{year:'1962',issue:'12',family:'Journal'},
 vision:reading({category:'vintage football collectible portrait panel',object_unit:'panel',photo_clues:[clue('Alex Alpha'),clue('Casey Delta')],identity_basis:{family:'not_applicable',variant:'physical_evidence'},unresolved_identity_fields:['family']}),
 candidates:[
  candidate({unit:'panel',matches:[match('ref1','layout','two portrait panel'),match('ref1','subject','Alex Alpha and Casey Delta')],fields:[field('ref1','subject','Alex Alpha','image'),field('ref1','subject','Casey Delta','image')]}),
  candidate({unit:'panel',matches:[match('ref2','layout','two portrait panel'),match('ref2','subject','Alex Alpha and Casey Delta')],fields:[field('ref2','family','Sports Journal','image'),field('ref2','year','1962','image'),field('ref2','subject','Alex Alpha','image'),field('ref2','subject','Casey Delta','image'),field('ref2','variant','HAND CUT','image')]}),
  candidate({unit:'panel',matches:[match('ref3','layout','two portrait panel'),match('ref3','subject','Alex Alpha and Casey Delta')],fields:[field('ref3','family','Sports Journal','text','parent'),field('ref3','year','1962'),field('ref3','issue_number','12','text','parent'),field('ref3','subject',subject),field('ref3','variant','Uncut collector photo')]})
 ],
 references:[reference('ref1','Alex Alpha Casey Delta',true),reference('ref2','Sports Journal 1962 Alex Alpha Casey Delta HAND CUT',true),reference('ref3','Sports Journal. 1962. No. 12. '+subject+'.')]
};
const box={
 expected:{brand:'Example',year:'2028-29'},
 vision:reading({category:'basketball trading card sealed box',object_unit:'box',brand:'Example',brand_confidence:99,family:'Example Chrome Update Series',photo_clues:[clue('Example Chrome'),clue('UPDATE SERIES'),clue('2028/29','season'),clue('1 AUTOGRAPH IN EVERY BOX!')],identity_basis:{family:'printed',variant:'inferred'},unresolved_identity_fields:['variant']}),
 candidates:[candidate({category:'basketball trading card sealed box',brand:'Example',family:'Example Chrome Updates Basketball',year:'2028-29',unit:'object',variant:'Hobby Box',decision:'different',same_unit:false,conflicts:[{scope:'target',reason:'The original is a closed box; the reference shows the box opened with packs.'}],matches:[match('ref4','layout','same front design'),match('ref4','text','Example Chrome UPDATE SERIES')],fields:[field('ref4','brand','Example'),field('ref4','family','Example Chrome Updates Basketball'),field('ref4','year','2028-29'),field('ref4','variant','Hobby Box')]})],
 references:[reference('ref4','Example. Example Chrome Updates Basketball. 2028-29. Hobby Box. 1 autograph per box!')]
};
const remote={
 vision:reading({category:'television remote control',brand:'Example',brand_confidence:95,family:'Example Smart TV remote',photo_clues:[clue('TV GUIDE'),clue('TOP PICKS'),clue('VOICE'),clue('NETFLIX')],identity_basis:{family:'inferred',variant:'physical_evidence'},unresolved_identity_fields:['family','variant'],next_photo_request:'Photograph the back and battery compartment model label.'}),
 candidates:[],references:[reference('ref1','Printer COPY SCAN A4 X100',true),reference('ref2','Audio controls BALANCE TEST Q200',true)]
};
module.exports={machamp,panel,box,remote};
