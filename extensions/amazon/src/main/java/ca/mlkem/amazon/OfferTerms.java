package ca.mlkem.amazon;

/** Reads only offer headings, adjoining lists and rate labels on the official merchant offer. */
final class OfferTerms {
    private OfferTerms() {}
    static final String SCRIPT = "(function(){'use strict';"
        + "if(window!==top||location.origin!=='https://aeroplan.rewardops.com'||"
        + "!/^\\/en-CA\\/affiliate-details\\/(amazon|walmart)$/.test(location.pathname))return null;"
        + "const out={included:[],excluded:[],rate:''};"
        + "const clean=e=>(e.textContent||'').replace(/\\s+/g,' ').trim();"
        + "const nodes=document.querySelectorAll('h1,h2,h3,h4,h5,[role=heading],strong');"
        + "for(const h of nodes){const t=clean(h);if(t.length>160)continue;"
        + "const match=t.match(/(?:up to |earn )?\\d+(?:\\.\\d+)?\\s*(?:pts?|points?)\\s*\\/\\s*\\$/i);"
        + "if(match&&!out.rate)out.rate=match[0];"
        + "const label=t.toLowerCase().replace(/[:.]$/,'');"
        + "const field=['eligible categories','included categories','qualifying categories'].includes(label)?'included':"
        + "['exclusions','excluded categories','ineligible categories'].includes(label)?'excluded':null;"
        + "if(!field)continue;const list=h.nextElementSibling||h.parentElement?.nextElementSibling;"
        + "if(!list||!['UL','OL'].includes(list.tagName))continue;"
        + "for(const li of Array.from(list.children).slice(0,100)){const name=clean(li);"
        + "if(li.tagName==='LI'&&name.length>1&&name.length<=64&&!/[.:;!?]/.test(name)&&!out[field].includes(name))out[field].push(name);}}"
        + "return out;})()";
}
