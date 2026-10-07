"use strict";
const search=document.getElementById("search"),category=document.getElementById("category"),cards=[...document.querySelectorAll("[data-category]")];
function filter(){const q=search.value.trim().normalize("NFKC").toLocaleLowerCase("ja");let visible=0;for(const card of cards){const match=(category.value==="all"||card.dataset.category===category.value)&&card.dataset.search.normalize("NFKC").toLocaleLowerCase("ja").includes(q);card.hidden=!match;if(match)visible++;}document.getElementById("count").textContent=`${visible} / ${cards.length} 件の機能`;document.getElementById("empty").hidden=visible!==0;}
search.addEventListener("input",filter);category.addEventListener("change",filter);filter();
