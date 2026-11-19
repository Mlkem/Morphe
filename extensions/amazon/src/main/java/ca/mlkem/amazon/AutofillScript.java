package ca.mlkem.amazon;

/** Fills only the observed official form. Never submits it or clicks a purchase control. */
public final class AutofillScript {
    private AutofillScript() {}

    public static String create(String number, String lastName) {
        return create(number, lastName, false);
    }

    public static String create(String number, String lastName, boolean manual) {
        return "(function(){'use strict';"
                + "if(window.top!==window||location.origin!=='https://aeroplan.rewardops.com'"
                + "||location.pathname!=='/en-CA/affiliate-details/amazon')return 'wrong-origin';"
                + "const n=document.getElementById('membershipNumber'),l=document.getElementById('lastName');"
                + "if(!n||!l||n.tagName!=='INPUT'||l.tagName!=='INPUT'||n.disabled||l.disabled"
                + "||n.getClientRects().length===0||l.getClientRects().length===0)return 'waiting';"
                + "if(n.value||l.value)return 'user-entered';"
                + "if(window.__aeroplanHelperFilled===n&&" + (!manual) + ")return 'already-filled';"
                + "const setter=Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set;"
                + "setter.call(n," + SessionRules.quote(number) + ");"
                + "n.dispatchEvent(new Event('input',{bubbles:true}));"
                + "n.dispatchEvent(new Event('change',{bubbles:true}));"
                + "setter.call(l," + SessionRules.quote(lastName) + ");"
                + "l.dispatchEvent(new Event('input',{bubbles:true}));"
                + "l.dispatchEvent(new Event('change',{bubbles:true}));"
                + "window.__aeroplanHelperFilled=n;return 'filled';})()";
    }
}
