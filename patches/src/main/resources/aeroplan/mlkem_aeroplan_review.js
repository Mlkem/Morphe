// Executed only in the visible retailer document. Returns a read-only page snapshot.
(function reviewPage(store, liveRules, referralAt) {
    "use strict";
    const amazon = store === "amazon";
    const domain = amazon ? "amazon.ca" : "walmart.ca";
    if (window !== window.top || location.protocol !== "https:" ||
        ![domain, "www." + domain, ...(amazon ? ["m.amazon.ca"] : [])].includes(location.hostname) ||
        (location.port && location.port !== "443")) return null;

    const path = location.pathname.toLowerCase();
    const matches = (node, selector) => Array.from(node.querySelectorAll(selector));
    const clean = (s, limit = 150) => String(s || "").replace(/\s+/g, " ").trim().slice(0, limit);
    const text = node => clean(node && node.textContent);
    const first = (node, sel) => text(node.querySelector(sel));
    const key = value => clean(value).normalize("NFD").replace(/[\u0300-\u036f]/g, "")
        .toLowerCase().replace(/&/g, " and ").replace(/[^a-z0-9]+/g, " ").trim();
    const rules = amazon ? [
        ["Toys & games", "toys and games", "jeux et jouets"],
        ["Video games", "video games", "jeux video"],
        ["Grocery & gourmet food", "grocery", "grocery and gourmet food", "epicerie"],
        ["Gift cards", "gift cards", "cartes cadeaux"],
        ["Automotive & tires", "automotive", "automotive and tires", "pneus et roues"],
        ["CDs, DVDs & vinyl", "cds and vinyl", "movies and tv", "dvd", "blu ray"],
        ["Digital content", "digital books", "digital music", "digital video", "kindle ebooks", "kindle unlimited"],
        ["Computers & components", "computers and accessories", "computers and tablets", "computer components", "tablets"],
        ["Electronics components & home audio", "electronic components", "home audio", "home entertainment"],
        ["Collectibles", "collectible items", "collectibles"]
    ] : [
        ["Grocery & food", "grocery", "groceries", "food", "alimentation", "epicerie"],
        ["Electronics", "electronics", "electronique"],
        ["Video games", "video games", "jeux video"]
    ];
    // Retailer departments can change independently of the offer. Do not infer from a title.
    const fresh = Date.now() >= Date.UTC(2026,10,19) && Date.now() < Date.UTC(2026,11,1,5);
    function categoryPage(doc) {
        const crumbs = matches(doc, '#wayfinding-breadcrumbs_feature_div a, [data-automation-id="breadcrumb"] a, [data-testid="breadcrumb"] a, nav[aria-label*="bread" i] a, nav[aria-label*="ariane" i] a, [itemtype$="BreadcrumbList"] [itemprop="name"]').map(text);
        if (!crumbs.length) {
            for (const el of matches(doc, 'script[type="application/ld+json"]').slice(0,25)) {
                try {
                    const obj = JSON.parse(el.textContent);
                    for (const o of [].concat(obj).flatMap(v => v && v["@graph"] || [v])) {
                        if (o && o["@type"] === "BreadcrumbList" && Array.isArray(o.itemListElement))
                            crumbs.push(...o.itemListElement.map(v => clean(v.name || (v.item && v.item.name))));
                    }
                } catch (_) {}
            }
        }
        return [...new Set(crumbs.filter(v => v && !["home","accueil","amazon ca","walmart ca"].includes(key(v))))].slice(0,12);
    }
    function assess(categories) {
        if (!categories.length) return {state:"unknown", group:"Category unavailable"};
        const exact = labels => Array.isArray(labels) && categories.some(c => labels.some(v => key(c) === key(v)));
        if (liveRules && exact(liveRules.excluded)) return {state:"flag", group:categories.find(c => liveRules.excluded.some(v => key(c) === key(v)))};
        if (liveRules && exact(liveRules.included)) return {state:"listed", group:categories.find(c => liveRules.included.some(v => key(c) === key(v)))};
        const rule = fresh && rules.find(row => categories.some(c => row.some(alias => key(alias) === key(c))));
        if (rule) return {state:"flag", group:rule[0]};
        if (!fresh) return {state:"unknown", group:"Check the current offer"};
        return {state:"review", group:categories[0]};
    }
    function productUrl(href) {
        try {
            const url = new URL(href, location.href);
            if (url.origin !== location.origin || url.username || url.password) return null;
            const m = amazon ? url.pathname.match(/\/(?:dp|gp\/product|gp\/aw\/d)\/([a-z0-9]{10})(?:\/|$)/i)
                : url.pathname.match(/^\/(?:en|fr)\/ip\/(?:[^/]+\/)?([a-z0-9_-]+)\/?$/i);
            if (!m) return null;
            const canonical = amazon ? "/dp/" + m[1] : url.pathname;
            return location.origin + canonical;
        } catch (_) { return null; }
    }
    // Use the renderer's ordinary HTTP cache for public product metadata. No account data is sent.
    const cache = window.__mlkemAeroplanCategories || (window.__mlkemAeroplanCategories = new Map());
    function fetchCategories(url) {
        if (!url || cache.has(url) || cache.size >= 100 ||
                Array.from(cache.values()).filter(v => v === null).length >= 2) return;
        cache.set(url,null);
        const controller = new AbortController();
        const timer = setTimeout(() => controller.abort(), 8000);
        (async () => {
            const response = await fetch(url, {credentials:"omit", referrerPolicy:"no-referrer", redirect:"error", signal:controller.signal});
            if (!response.ok || !/text\/html/i.test(response.headers.get("content-type") || "")) throw Error();
            const reader = response.body.getReader(), decoder = new TextDecoder();
            let raw = "", size = 0;
            try {
                while (true) {
                    const part = await reader.read();
                    if (part.done) break;
                    size += part.value.length;
                    if (size > 4 * 1024 * 1024) throw Error();
                    raw += decoder.decode(part.value, {stream:true});
                }
                raw += decoder.decode();
            } finally { await reader.cancel().catch(() => {}); }
            const fragment = document.createElement("template");
            fragment.innerHTML = raw;
            cache.set(url, categoryPage(fragment.content));
        })().catch(() => cache.set(url, [])).finally(() => clearTimeout(timer));
    }
    const confirmation = amazon ? /\/(?:gp\/buy\/thankyou|checkout\/.*thank)/.test(path)
        : /\/(?:thank-?you|order-?confirmation)(?:\/|$)/.test(path);
    if (confirmation) {
        const pattern = amazon ? /\b\d{3}-\d{7}-\d{7}\b/ : /\b\d{10,20}\b/;
        const fields = matches(document, '[data-order-id], [data-automation-id*="order-number" i], [data-testid*="order-number" i], [id*="order-number" i], [id*="orderId" i], [class*="order-number" i]');
        let id = "";
        for (const el of fields) {
            const match = (el.getAttribute("data-order-id") || text(el)).match(pattern);
            if (match) { id = match[0]; break; }
        }
        return {kind:"order", id};
    }
    const cart = amazon ? /\/(?:gp\/(?:cart|aw\/c)|cart)(?:\/|$)/.test(path) : /\/cart(?:\/|$)/.test(path);
    const checkout = /\/(?:checkout|gp\/buy|payment)(?:\/|$)/.test(path);
    if (!cart && !checkout) {
        if (!productUrl(location.href)) return {kind:"store"};
        const categories = categoryPage(document);
        return {kind:"product", ...assess(categories)};
    }
    const selector = amazon ? '#sc-active-cart .sc-list-item[data-asin], #activeCartViewForm .sc-list-item[data-asin], #sc-active-cart [data-item-index][data-asin], [data-testid="checkout-item"], #checkout [data-csa-c-type="item"][data-asin]'
        : '[data-automation-id="cart-item"], [data-automation-id="cart-item-tile"], [data-testid="cart-item"], [data-testid="checkout-item"], [data-automation-id="checkout-item"], [data-automation-id="cart-list"] [data-automation-id="product-tile"]';
    const nodes = matches(document, selector).filter(el => !el.closest('[hidden], [aria-hidden="true"], #sc-saved-cart, [data-testid="saved-for-later"], [data-automation-id="recommendations"], [id*="recommendation" i]') &&
        !el.parentElement?.closest(selector));
    function cartTiming(el) {
        const added = el.getAttribute("data-addedtime");
        if (!Number.isSafeInteger(referralAt) || referralAt <= 0 || !/^\d{13}$/.test(added || "")) return "unknown";
        const time = Number(added);
        if (time > Date.now() || referralAt > Date.now() || time === referralAt) return "unknown";
        return time < referralAt ? "before" : "after";
    }
    const rows = nodes.slice(0,100).map((el, index) => {
        const name = first(el,'.sc-product-title .a-truncate-full, .sc-product-title, [data-automation-id="product-title"], [data-testid="product-title"], [itemprop="name"]');
        const link = matches(el,'a[href]').find(a => productUrl(a.href));
        const url = link ? productUrl(link.href) : null;
        const categories = [el.getAttribute("data-category"), first(el,'[data-automation-id="department"], [itemprop="category"]')].filter(Boolean);
        if (!categories.length && url) { fetchCategories(url); categories.push(...(cache.get(url) || [])); }
        const qty = el.getAttribute("data-quantity") || first(el,'[data-automation-id="quantity"], .sc-product-quantity, [data-testid="quantity"]');
        const picked = clean(qty).match(/^(?:(?:qty|quantity|quantité)[: ]+)?(\d+)$/i);
        const quantity = picked && Number(picked[1]) > 0 ? Number(picked[1]) : null;
        return {title: name || text(link) || "Cart line " + (index+1), quantity, ...(amazon ? {timing:cartTiming(el)} : {}), ...assess(categories)};
    });
    const countText = first(document, '#sc-subtotal-label-activecart, #sc-subtotal-label-buybox, [data-testid="cart-item-count"], [data-automation-id="cart-item-count"], main h1');
    const declared = countText.match(/\b(\d+)\s*(?:items?|articles?)\b/i);
    const whole = !!declared && nodes.length <= 100 && rows.length > 0 && rows.every(r=>r.quantity !== null) &&
        rows.reduce((n,r)=>n+r.quantity,0) === Number(declared[1]);
    return {kind:checkout ? "checkout" : "cart", rows, whole, declared:declared ? Number(declared[1]) : null};
})
