# Ingestion run sample-20261110T153604Z

Generated: 2026-11-10T15:47:30.206Z  
Status: AWAITING_REVIEW

## Totals

| metric | value |
|---|---:|
| fetched rows | 146 |
| candidates | 36 |
| rejected rows | 202 |
| rows left out by caps | 0 |

## By source and metro

| source | metro | fetched | candidates | rejected | capped |
|---|---|---:|---:|---:|---:|
| hms | Austin | 1 | 0 | 1 | 0 |
| hms | Dallas | 32 | 1 | 31 | 0 |
| hfsaa | Dallas | 2 | 0 | 2 | 0 |
| hms | NYC | 71 | 16 | 55 | 0 |
| hfsaa | NYC | 22 | 7 | 15 | 0 |
| hma | Toronto | 18 | 12 | 6 | 0 |

## Trust coverage

| metric | value |
|---|---:|
| Tier-1 (registry-verified) candidates | 36 |
| manual candidates | 0 |
| hand-cut TRUE | 36 |
| hand-cut FALSE | 0 |
| hand-cut NULL | 0 |
| claims | 36 |

## Enrichment

| metric | value |
|---|---:|
| admitted as restaurants | 36 |
| excluded as non-restaurants | 6 |
| undecided (held for a curator) | 0 |
| cuisine set | 5 |
| cuisine conflicts | 0 |
| weak (name-keyword) decisions | 4 |

### Admitted by type

| type | count |
|---|---:|
| RESTAURANT | 36 |

### Excluded by class

| class | count | example reason |
|---|---:|---|
| BUTCHER | 6 | butcher: name: meat market + butcher + poultry |

_Photo pass not run for this batch._

## Candidates and claims

Every row below is a candidate for the reviewed load. The claim keys are the
citation identity the load matches on; a claim already on another listing is a
conflict that needs a curator decision.

| name | city | metro | tier | status | hand-cut | cross-contamination | claims |
|---|---|---|---|---|---|---|---|
| Imperial Biryani | Richardson | Dallas | TIER_1 | VERIFIED | true | NO_CROSS_CONTAMINATION | hms::2004 |
| Burgermania 39 Street | New York | NYC | TIER_1 | VERIFIED | true | NO_CROSS_CONTAMINATION | hms::1839 |
| Burgermania 40 Street | New York | NYC | TIER_1 | VERIFIED | true | NO_CROSS_CONTAMINATION | hms::61 |
| Burgery | Jamaica | NYC | TIER_1 | VERIFIED | true | NO_CROSS_CONTAMINATION | hms::168 |
| Grillwaale | Uniondale | NYC | TIER_1 | VERIFIED | true | NO_CROSS_CONTAMINATION | hms::116 |
| Gyro King | Brooklyn | NYC | TIER_1 | VERIFIED | true | NO_CROSS_CONTAMINATION | hfsaa::R-23E6F5CB1A |
| Gyro King Express | Brooklyn | NYC | TIER_1 | VERIFIED | true | NO_CROSS_CONTAMINATION | hfsaa::R-5D886F9E55 |
| Holy Burger Jersey City | Jersey City | NYC | TIER_1 | VERIFIED | true | NO_CROSS_CONTAMINATION | hms::3175 |
| Holy Cow Fulton Street | Brooklyn | NYC | TIER_1 | VERIFIED | true | NO_CROSS_CONTAMINATION | hms::485 |
| Holy Cow Hicksville | Hicksville | NYC | TIER_1 | VERIFIED | true | NO_CROSS_CONTAMINATION | hms::151 |
| Holy Cow Midtown | New York | NYC | TIER_1 | VERIFIED | true | NO_CROSS_CONTAMINATION | hms::2221 |
| Holy Cow Parkchester | Bronx | NYC | TIER_1 | VERIFIED | true | NO_CROSS_CONTAMINATION | hms::947 |
| Holy Cow Upper West Side | New York | NYC | TIER_1 | VERIFIED | true | NO_CROSS_CONTAMINATION | hms::513 |
| Hyderabad House JC | Jersey City | NYC | TIER_1 | VERIFIED | true | NO_CROSS_CONTAMINATION | hms::71 |
| Kababjees | Hicksville | NYC | TIER_1 | VERIFIED | true | NO_CROSS_CONTAMINATION | hms::31 |
| Khorasan Kabab | Metuchen | NYC | TIER_1 | VERIFIED | true | NO_CROSS_CONTAMINATION | hms::557 |
| NYU Lipton Hall | New York | NYC | TIER_1 | VERIFIED | true | NO_CROSS_CONTAMINATION | hfsaa::R-E9D902DEB0 |
| Pariwaar Delights | Hoboken | NYC | TIER_1 | VERIFIED | true | NO_CROSS_CONTAMINATION | hms::1733 |
| Samosa Paradise | Jersey City | NYC | TIER_1 | VERIFIED | true | NO_CROSS_CONTAMINATION | hms::44 |
| Sheikhs N Burgers Jericho | Jericho | NYC | TIER_1 | VERIFIED | true | NO_CROSS_CONTAMINATION | hms::3260 |
| Sizzlings | Oceanside | NYC | TIER_1 | VERIFIED | true | NO_CROSS_CONTAMINATION | hfsaa::R-051B3E4FD3 |
| Sunshine Hicksville | Hicksville | NYC | TIER_1 | VERIFIED | true | NO_CROSS_CONTAMINATION | hfsaa::R-01EFA989BF |
| Uncle’s Fried Chicken | Jamaica | NYC | TIER_1 | VERIFIED | true | NO_CROSS_CONTAMINATION | hfsaa::R-463C67F575 |
| Zaitoon Kitchen | The Bronx | NYC | TIER_1 | VERIFIED | true | NO_CROSS_CONTAMINATION | hfsaa::R-3167E06147 |
| D Spot Dessert Scarborough Lebovic | Scarborough | Toronto | TIER_1 | VERIFIED | true | NO_CROSS_CONTAMINATION | hma::22049 |
| Fortinos Bolton | Bolton | Toronto | TIER_1 | VERIFIED | true | NO_CROSS_CONTAMINATION | hma::22190 |
| Fortinos Brampton Mountainash | Canada | Toronto | TIER_1 | VERIFIED | true | NO_CROSS_CONTAMINATION | hma::22133 |
| Fortinos Brampton Quarry Edge | Canada | Toronto | TIER_1 | VERIFIED | true | NO_CROSS_CONTAMINATION | hma::22173 |
| Fortinos Brampton Worthington | Canada | Toronto | TIER_1 | VERIFIED | true | NO_CROSS_CONTAMINATION | hma::22180 |
| Fortinos Etobicoke Queens Plate | Canada | Toronto | TIER_1 | VERIFIED | true | NO_CROSS_CONTAMINATION | hma::22197 |
| Kara Mia | Canada L3S3K1 | Toronto | TIER_1 | VERIFIED | true | NO_CROSS_CONTAMINATION | hma::22556 |
| Kara Mia Oakville | Oakville | Toronto | TIER_1 | VERIFIED | true | NO_CROSS_CONTAMINATION | hma::34765 |
| Lahore Tikka House | Toronto | Toronto | TIER_1 | VERIFIED | true | NO_CROSS_CONTAMINATION | hma::22915 |
| Paramount Fine Foods Queensway | Toronto | Toronto | TIER_1 | VERIFIED | true | NO_CROSS_CONTAMINATION | hma::22292 |
| Royal Jasmine | L4W4G1 Mississauga | Toronto | TIER_1 | VERIFIED | true | NO_CROSS_CONTAMINATION | hma::22639 |
| The Kebob | ON L4T 1A8 Canada | Toronto | TIER_1 | VERIFIED | true | NO_CROSS_CONTAMINATION | hma::22659 |

## Rejected rows

| reason | source | record | name | detail |
|---|---|---|---|---|
| AMBIGUOUS_GEOCODER_MATCH | hma | 22539 | Sobeys Mississauga | geocoder matched 'Physiomed', which does not share a name or city with 'Sobeys Mississauga' |
| AMBIGUOUS_GEOCODER_MATCH | hma | 22807 | Lajawab | geocoder matched 'Markham Road, Toronto', which does not share a name or city with 'Lajawab' |
| INVALID_CANDIDATE | hma | 22858 | Pizzally Scarborough | row has no name or street address |
| INVALID_CANDIDATE | hma | 28835 | Chachees Chai Cafe Scarborough | row has no name or street address |
| NOT_A_RESTAURANT | hfsaa | M-29D3BDCB66 | Jamaica Live Poultry Meat Market [Butcher Dept. Only] | butcher: name: meat market + butcher + poultry |
| NOT_A_RESTAURANT | hfsaa | M-693DCD1365 | Alladin Halal Meat & Grocery [Butcher Dept. ONLY] | butcher: name: butcher + grocery |
| NOT_A_RESTAURANT | hfsaa | M-6DAFF99B39 | Hamza Meat Shop [Butcher Dept. ONLY] | butcher: name: meat shop + butcher |
| NOT_A_RESTAURANT | hfsaa | M-86DF8BC1B3 | Mach Bazar [Butcher Dept. ONLY] | butcher: name: butcher |
| NOT_A_RESTAURANT | hfsaa | M-A4CEC75320 | Barakat Halal Market [Butcher Dept. ONLY] | butcher: name: butcher |
| NOT_A_RESTAURANT | hfsaa | M-EAD41B252C | Eden’s Butchery & Deli [Butcher Dept. Only] | butcher: name: butcher |
| NO_GEOCODER_MATCH | hms | 3270 | Sheikhs N Burgers (Skokie) | no geocoder match for 'Sheikhs N Burgers (Skokie)' |
| NO_GEOCODER_MATCH | hms | 3245 | Spice Wok Frisco | no geocoder match for 'Spice Wok Frisco' |
| NO_GEOCODER_MATCH | hms | 3244 | JK Kabab Express (Devon) | no geocoder match for 'JK Kabab Express (Devon)' |
| NO_GEOCODER_MATCH | hms | 3211 | Gravitea | no geocoder match for 'Gravitea' |
| NO_GEOCODER_MATCH | hms | 3209 | Kick Starrt | no geocoder match for 'Kick Starrt' |
| NO_GEOCODER_MATCH | hms | 3208 | The Fry Fusion | no geocoder match for 'The Fry Fusion' |
| NO_GEOCODER_MATCH | hms | 3207 | Meltwich (Lombard Location Only) | no geocoder match for 'Meltwich (Lombard Location Only)' |
| NO_GEOCODER_MATCH | hms | 3205 | Desi Chowk | no geocoder match for 'Desi Chowk' |
| NO_GEOCODER_MATCH | hms | 3204 | Masala Twist Xpress Sachse | no geocoder match for 'Masala Twist Xpress Sachse' |
| NO_GEOCODER_MATCH | hms | 3192 | Mike's Chicken and Sushi | no geocoder match for 'Mike's Chicken and Sushi' |
| NO_GEOCODER_MATCH | hms | 3172 | Smile Burger | no geocoder match for 'Smile Burger' |
| NO_GEOCODER_MATCH | hms | 3151 | Shan Grills | no geocoder match for 'Shan Grills' |
| NO_GEOCODER_MATCH | hms | 2395 | Tandoori Pizza & Grill | no geocoder match for 'Tandoori Pizza & Grill' |
| NO_GEOCODER_MATCH | hms | 2037 | Halal Burgers | no geocoder match for 'Halal Burgers' |
| NO_GEOCODER_MATCH | hms | 2036 | Halal Burgers | no geocoder match for 'Halal Burgers' |
| NO_GEOCODER_MATCH | hms | 2030 | Cafe de Khan Murphy | no geocoder match for 'Cafe de Khan Murphy' |
| NO_GEOCODER_MATCH | hms | 2001 | Tandoor Samsa | no geocoder match for 'Tandoor Samsa' |
| NO_GEOCODER_MATCH | hms | 1994 | Patelli's Gyros & Beef | no geocoder match for 'Patelli's Gyros & Beef' |
| NO_GEOCODER_MATCH | hms | 1867 | Taco Verde | no geocoder match for 'Taco Verde' |
| NO_GEOCODER_MATCH | hms | 552 | Firebird Piri Piri Grill | no geocoder match for 'Firebird Piri Piri Grill' |
| NO_GEOCODER_MATCH | hms | 509 | Zaiqa House | no geocoder match for 'Zaiqa House' |
| NO_GEOCODER_MATCH | hms | 489 | Pali Pali Pizza & Korean Chicken | no geocoder match for 'Pali Pali Pizza & Korean Chicken' |
| NO_GEOCODER_MATCH | hms | 254 | Hangry Joe's Arlington Heights and Chicago Pie | no geocoder match for 'Hangry Joe's Arlington Heights and Chicago Pie' |
| NO_GEOCODER_MATCH | hms | 249 | Kababish Indian Cuisine | no geocoder match for 'Kababish Indian Cuisine' |
| NO_GEOCODER_MATCH | hms | 238 | Al Haramain Restaurant | no geocoder match for 'Al Haramain Restaurant' |
| NO_GEOCODER_MATCH | hms | 162 | Hello Shawarma (Hello Thai Not HMS Certified) | no geocoder match for 'Hello Shawarma (Hello Thai Not HMS Certified)' |
| NO_GEOCODER_MATCH | hms | 158 | Zaafran Kitchen | no geocoder match for 'Zaafran Kitchen' |
| NO_GEOCODER_MATCH | hms | 147 | Byrd's Hot Chicken Oak Lawn | no geocoder match for 'Byrd's Hot Chicken Oak Lawn' |
| NO_GEOCODER_MATCH | hms | 144 | Taste of Persia | no geocoder match for 'Taste of Persia' |
| NO_GEOCODER_MATCH | hms | 139 | Baba's Restaurant | no geocoder match for 'Baba's Restaurant' |
| NO_GEOCODER_MATCH | hms | 129 | Deccan Bawarchi | no geocoder match for 'Deccan Bawarchi' |
| NO_GEOCODER_MATCH | hms | 112 | Bison Burger | no geocoder match for 'Bison Burger' |
| NO_GEOCODER_MATCH | hms | 103 | Arzan Market & Cafe | no geocoder match for 'Arzan Market & Cafe' |
| NO_GEOCODER_MATCH | hms | 99 | Food Paradise | no geocoder match for 'Food Paradise' |
| NO_GEOCODER_MATCH | hms | 89 | Classic International Market (Kitchen) | no geocoder match for 'Classic International Market (Kitchen)' |
| NO_GEOCODER_MATCH | hms | 74 | Cultura Subs | no geocoder match for 'Cultura Subs' |
| NO_GEOCODER_MATCH | hms | 68 | Byrd's Hot Chicken Algonquin | no geocoder match for 'Byrd's Hot Chicken Algonquin' |
| NO_GEOCODER_MATCH | hms | 59 | Two Brothers Chicken & Sub (ONLY THIS LOCATION) | no geocoder match for 'Two Brothers Chicken & Sub (ONLY THIS LOCATION)' |
| NO_GEOCODER_MATCH | hms | 52 | Khizer's Kitchen | no geocoder match for 'Khizer's Kitchen' |
| NO_GEOCODER_MATCH | hms | 49 | Shalimar Restaurant | no geocoder match for 'Shalimar Restaurant' |
| NO_GEOCODER_MATCH | hms | 45 | Ali Baba Kabab | no geocoder match for 'Ali Baba Kabab' |
| NO_GEOCODER_MATCH | hfsaa | R-E9A157D511 | Port of Peri Peri | no geocoder match for 'Port of Peri Peri' |
| NO_GEOCODER_MATCH | hma | 34538 | B BOYZ THE BURGER ARCHITECTS | no geocoder match for 'B BOYZ THE BURGER ARCHITECTS' |
| NO_GEOCODER_MATCH | hma | 32387 | The Kabab Shoppe | no geocoder match for 'The Kabab Shoppe' |
| NO_GEOCODER_MATCH | hma | 30477 | Dspot Desserts Erin Mills | no geocoder match for 'Dspot Desserts Erin Mills' |
| NO_GEOCODER_MATCH | hma | 24326 | D Spot Desserts Ellesmere | no geocoder match for 'D Spot Desserts Ellesmere' |
| NO_GEOCODER_MATCH | hma | 24201 | Chachee’s Chai Cafe Thorncliffe | no geocoder match for 'Chachee’s Chai Cafe Thorncliffe' |
| NO_GEOCODER_MATCH | hma | 22960 | Sahib Mandi & Grill | no geocoder match for 'Sahib Mandi & Grill' |
| NO_GEOCODER_MATCH | hma | 22933 | Loblaws(Lakeshore and Bathurst) | no geocoder match for 'Loblaws(Lakeshore and Bathurst)' |
| NO_GEOCODER_MATCH | hma | 22897 | Burger Factory Waterloo | no geocoder match for 'Burger Factory Waterloo' |
| NO_GEOCODER_MATCH | hma | 22875 | Mama’s Meat Shoppe Windsor | no geocoder match for 'Mama’s Meat Shoppe Windsor' |
| NO_GEOCODER_MATCH | hma | 22843 | Mini Moe’s Burgers Scarborough | no geocoder match for 'Mini Moe’s Burgers Scarborough' |
| NO_GEOCODER_MATCH | hma | 22822 | Chainsmoker Urban Halal Barbecue Scarborough | no geocoder match for 'Chainsmoker Urban Halal Barbecue Scarborough' |
| NO_GEOCODER_MATCH | hma | 22788 | Afghan Bbq & Pizza | no geocoder match for 'Afghan Bbq & Pizza' |
| NO_GEOCODER_MATCH | hma | 22766 | Chicken Kitchen Warden & Lawrence | no geocoder match for 'Chicken Kitchen Warden & Lawrence' |
| NO_GEOCODER_MATCH | hma | 22759 | Chicken Kitchen Sheppard | no geocoder match for 'Chicken Kitchen Sheppard' |
| NO_GEOCODER_MATCH | hma | 22746 | Chicken & Sea food Lawrence | no geocoder match for 'Chicken & Sea food Lawrence' |
| NO_GEOCODER_MATCH | hma | 22708 | Chicken & Sea food Pickering | no geocoder match for 'Chicken & Sea food Pickering' |
| NO_GEOCODER_MATCH | hma | 22697 | Chick Fiesta Pickering | no geocoder match for 'Chick Fiesta Pickering' |
| NO_GEOCODER_MATCH | hma | 22691 | Kara Mia Ottawa | no geocoder match for 'Kara Mia Ottawa' |
| NO_GEOCODER_MATCH | hma | 22673 | Yumy’s Chicken | no geocoder match for 'Yumy’s Chicken' |
| NO_GEOCODER_MATCH | hma | 22613 | Jaffy’s Premium Grill | no geocoder match for 'Jaffy’s Premium Grill' |
| NO_GEOCODER_MATCH | hma | 22423 | Galito’s Flame Grilled Chicken | no geocoder match for 'Galito’s Flame Grilled Chicken' |
| NO_GEOCODER_MATCH | hma | 22403 | Leymoon | no geocoder match for 'Leymoon' |
| NO_GEOCODER_MATCH | hma | 22392 | Boba Shack St.Catherines | no geocoder match for 'Boba Shack St.Catherines' |
| NO_GEOCODER_MATCH | hma | 22384 | Boba Shack Erin Mills | no geocoder match for 'Boba Shack Erin Mills' |
| NO_GEOCODER_MATCH | hma | 22377 | Boba Shack Guelph | no geocoder match for 'Boba Shack Guelph' |
| NO_GEOCODER_MATCH | hma | 22337 | Paramount Fine Foods Union Station | no geocoder match for 'Paramount Fine Foods Union Station' |
| NO_GEOCODER_MATCH | hma | 22316 | Paramount Fine Foods Downtown Toronto | no geocoder match for 'Paramount Fine Foods Downtown Toronto' |
| NO_GEOCODER_MATCH | hma | 22309 | Paramount Fine Foods Victoria Park & Sheppard | no geocoder match for 'Paramount Fine Foods Victoria Park & Sheppard' |
| NO_GEOCODER_MATCH | hma | 22302 | Paramount Fine Foods Niagara Falls | no geocoder match for 'Paramount Fine Foods Niagara Falls' |
| NO_GEOCODER_MATCH | hma | 22285 | Paramount Fine Foods Terminal 3 Airport | no geocoder match for 'Paramount Fine Foods Terminal 3 Airport' |
| NO_GEOCODER_MATCH | hma | 22243 | Paramount Fine Foods Burlington | no geocoder match for 'Paramount Fine Foods Burlington' |
| NO_GEOCODER_MATCH | hma | 22236 | Fortinos Stoney Creek | no geocoder match for 'Fortinos Stoney Creek' |
| NO_GEOCODER_MATCH | hma | 22223 | Fortinos Oakville Neyagawa | no geocoder match for 'Fortinos Oakville Neyagawa' |
| NO_GEOCODER_MATCH | hma | 22111 | Dspot Desserts Cafe Windsor | no geocoder match for 'Dspot Desserts Cafe Windsor' |
| NO_GEOCODER_MATCH | hma | 22092 | Dspot Desserts Richmond Hill | no geocoder match for 'Dspot Desserts Richmond Hill' |
| NO_GEOCODER_MATCH | hma | 22076 | Dspot Desserts Toronto Stockyards | no geocoder match for 'Dspot Desserts Toronto Stockyards' |
| NO_GEOCODER_MATCH | hma | 22031 | Dspot Desserts Kanata | no geocoder match for 'Dspot Desserts Kanata' |
| NO_GEOCODER_MATCH | hma | 22024 | Dspot Desserts Oshawa | no geocoder match for 'Dspot Desserts Oshawa' |
| NO_GEOCODER_MATCH | hma | 22017 | Dspot Desserts Oakville | no geocoder match for 'Dspot Desserts Oakville' |
| NO_GEOCODER_MATCH | hma | 21997 | Dspot Desserts Heartland | no geocoder match for 'Dspot Desserts Heartland' |
| NO_GEOCODER_MATCH | hma | 21980 | Dspot Desserts Mississauga – Dixie | no geocoder match for 'Dspot Desserts Mississauga – Dixie' |
| NO_GEOCODER_MATCH | hma | 21964 | Dspot Desserts Markham | no geocoder match for 'Dspot Desserts Markham' |
| NO_GEOCODER_MATCH | hma | 21954 | Dspot Desserts Kitchener | no geocoder match for 'Dspot Desserts Kitchener' |
| NO_GEOCODER_MATCH | hma | 21893 | Dspot Desserts Cambridge | no geocoder match for 'Dspot Desserts Cambridge' |
| NO_GEOCODER_MATCH | hma | 21868 | Dspot Desserts Barrie | no geocoder match for 'Dspot Desserts Barrie' |
| NO_GEOCODER_MATCH | hma | 21858 | Dspot Desserts Brantford | no geocoder match for 'Dspot Desserts Brantford' |
| NO_GEOCODER_MATCH | hma | 21842 | Dspot Desserts Queen St | no geocoder match for 'Dspot Desserts Queen St' |
| NO_GEOCODER_MATCH | hma | 21820 | Dspot Desserts Brampton | no geocoder match for 'Dspot Desserts Brampton' |
| NO_GEOCODER_MATCH | hma | 21802 | D Spot Desserts Ajax | no geocoder match for 'D Spot Desserts Ajax' |
| NO_GEOCODER_MATCH | hma | 19293 | Burger Factory Cambridge | no geocoder match for 'Burger Factory Cambridge' |
| NO_GEOCODER_MATCH | hms | 100 | Smokin Grill Xpress | no geocoder match for 'Smokin Grill Xpress' |
| NO_GEOCODER_MATCH | hms | 104 | Pizza Stop | no geocoder match for 'Pizza Stop' |
| NO_GEOCODER_MATCH | hms | 110 | Deccan Grill Irving | no geocoder match for 'Deccan Grill Irving' |
| NO_GEOCODER_MATCH | hms | 118 | Turan Uyghur Kitchen (Plano) | no geocoder match for 'Turan Uyghur Kitchen (Plano)' |
| NO_GEOCODER_MATCH | hms | 120 | SPICE WOK | no geocoder match for 'SPICE WOK' |
| NO_GEOCODER_MATCH | hms | 142 | BBQ King Express (Plano) | no geocoder match for 'BBQ King Express (Plano)' |
| NO_GEOCODER_MATCH | hms | 1633 | Firin Bakery | no geocoder match for 'Firin Bakery' |
| NO_GEOCODER_MATCH | hms | 1819 | One Stop India Restaurant | no geocoder match for 'One Stop India Restaurant' |
| NO_GEOCODER_MATCH | hms | 3152 | Madina Market (Kitchen) | no geocoder match for 'Madina Market (Kitchen)' |
| NO_GEOCODER_MATCH | hms | 3195 | Pizza Stop Wylie | no geocoder match for 'Pizza Stop Wylie' |
| NO_GEOCODER_MATCH | hms | 3226 | Holy Burger Dallas | no geocoder match for 'Holy Burger Dallas' |
| NO_GEOCODER_MATCH | hms | 3235 | LITTLE ASIA BAZAR | no geocoder match for 'LITTLE ASIA BAZAR' |
| NO_GEOCODER_MATCH | hms | 3239 | KEBAB UNCLE (Coppell) | no geocoder match for 'KEBAB UNCLE (Coppell)' |
| NO_GEOCODER_MATCH | hms | 3242 | KEBAB UNCLE (Plano) | no geocoder match for 'KEBAB UNCLE (Plano)' |
| NO_GEOCODER_MATCH | hms | 3285 | Bite Burst | no geocoder match for 'Bite Burst' |
| NO_GEOCODER_MATCH | hms | 3298 | Sal’s Nashville Hot Chicken | no geocoder match for 'Sal’s Nashville Hot Chicken' |
| NO_GEOCODER_MATCH | hms | 3324 | Cloud Naan | no geocoder match for 'Cloud Naan' |
| NO_GEOCODER_MATCH | hms | 3361 | The Gyro King | no geocoder match for 'The Gyro King' |
| NO_GEOCODER_MATCH | hms | 3363 | Afghan Asian Market (Kitchen) | no geocoder match for 'Afghan Asian Market (Kitchen)' |
| NO_GEOCODER_MATCH | hms | 3383 | MOCHINUT Murphy | no geocoder match for 'MOCHINUT Murphy' |
| NO_GEOCODER_MATCH | hms | 3387 | Everything Halal (Hurst) | no geocoder match for 'Everything Halal (Hurst)' |
| NO_GEOCODER_MATCH | hms | 3396 | Sal's Hot Chicken | no geocoder match for 'Sal's Hot Chicken' |
| NO_GEOCODER_MATCH | hms | 50 | BBQ King | no geocoder match for 'BBQ King' |
| NO_GEOCODER_MATCH | hms | 539 | Everything Halal | no geocoder match for 'Everything Halal' |
| NO_GEOCODER_MATCH | hms | 57 | Poblano's Piri Piri | no geocoder match for 'Poblano's Piri Piri' |
| NO_GEOCODER_MATCH | hms | 73 | BBQ King Express & Pizzeria | no geocoder match for 'BBQ King Express & Pizzeria' |
| NO_GEOCODER_MATCH | hms | 80 | Crescent Moon | no geocoder match for 'Crescent Moon' |
| NO_GEOCODER_MATCH | hms | 81 | COCONUT HILL KITCHEN | no geocoder match for 'COCONUT HILL KITCHEN' |
| NO_GEOCODER_MATCH | hms | 83 | SAL’S NASHVILLE HOT CHICKEN | no geocoder match for 'SAL’S NASHVILLE HOT CHICKEN' |
| NO_GEOCODER_MATCH | hms | 92 | Fiery Hot Nashville Chicken | no geocoder match for 'Fiery Hot Nashville Chicken' |
| NO_GEOCODER_MATCH | hms | 98 | Texas King Irving | no geocoder match for 'Texas King Irving' |
| NO_GEOCODER_MATCH | hfsaa | R-76F287AC85 | Chi Tea | no geocoder match for 'Chi Tea' |
| NO_GEOCODER_MATCH | hfsaa | R-FD2B8100CD | Shaahi Biryani | no geocoder match for 'Shaahi Biryani' |
| NO_GEOCODER_MATCH | hms | 3288 | Mirchi Boyz | no geocoder match for 'Mirchi Boyz' |
| NO_GEOCODER_MATCH | hms | 10 | Holy Cow Restaurant Inc. | no geocoder match for 'Holy Cow Restaurant Inc.' |
| NO_GEOCODER_MATCH | hms | 113 | Labbaik Karahi Restaurant | no geocoder match for 'Labbaik Karahi Restaurant' |
| NO_GEOCODER_MATCH | hms | 115 | Holy Cow Uniondale | no geocoder match for 'Holy Cow Uniondale' |
| NO_GEOCODER_MATCH | hms | 1466 | Al Mehran Restaurant | no geocoder match for 'Al Mehran Restaurant' |
| NO_GEOCODER_MATCH | hms | 148 | Datar Gyro & Grill | no geocoder match for 'Datar Gyro & Grill' |
| NO_GEOCODER_MATCH | hms | 163 | Holy Cow Mount Vernon | no geocoder match for 'Holy Cow Mount Vernon' |
| NO_GEOCODER_MATCH | hms | 1702 | Dosa and Biryani House | no geocoder match for 'Dosa and Biryani House' |
| NO_GEOCODER_MATCH | hms | 1732 | New York City Halal Grill (Aria Kabab) | no geocoder match for 'New York City Halal Grill (Aria Kabab)' |
| NO_GEOCODER_MATCH | hms | 1960 | Krunchy Krust | no geocoder match for 'Krunchy Krust' |
| NO_GEOCODER_MATCH | hms | 1961 | Holy Cow Hillside | no geocoder match for 'Holy Cow Hillside' |
| NO_GEOCODER_MATCH | hms | 227 | Eatzy Chinese | no geocoder match for 'Eatzy Chinese' |
| NO_GEOCODER_MATCH | hms | 230 | Mannan Bakery & Restaurant | no geocoder match for 'Mannan Bakery & Restaurant' |
| NO_GEOCODER_MATCH | hms | 231 | Mannan Bakery Factory | no geocoder match for 'Mannan Bakery Factory' |
| NO_GEOCODER_MATCH | hms | 235 | Azka BBQ | no geocoder match for 'Azka BBQ' |
| NO_GEOCODER_MATCH | hms | 237 | Sagar Restaurant (ONLY THIS RESTAURANT, VERIFY ADDRESS) | no geocoder match for 'Sagar Restaurant (ONLY THIS RESTAURANT, VERIFY ADDRESS)' |
| NO_GEOCODER_MATCH | hms | 239 | Moho Mexican Grill | no geocoder match for 'Moho Mexican Grill' |
| NO_GEOCODER_MATCH | hms | 240 | Dhaka Restaurant | no geocoder match for 'Dhaka Restaurant' |
| NO_GEOCODER_MATCH | hms | 2575 | Affan Indo-Chinese & Bangladeshi Restaurant | no geocoder match for 'Affan Indo-Chinese & Bangladeshi Restaurant' |
| NO_GEOCODER_MATCH | hms | 2576 | Holy Cow Astoria | no geocoder match for 'Holy Cow Astoria' |
| NO_GEOCODER_MATCH | hms | 3164 | Mannan Bakery and Grill | no geocoder match for 'Mannan Bakery and Grill' |
| NO_GEOCODER_MATCH | hms | 3189 | Holy Burger Pisctataway | no geocoder match for 'Holy Burger Pisctataway' |
| NO_GEOCODER_MATCH | hms | 3196 | Ace Shawarma & Burgers | no geocoder match for 'Ace Shawarma & Burgers' |
| NO_GEOCODER_MATCH | hms | 3215 | Saffron Restaurant | no geocoder match for 'Saffron Restaurant' |
| NO_GEOCODER_MATCH | hms | 3251 | Chick N Kick | no geocoder match for 'Chick N Kick' |
| NO_GEOCODER_MATCH | hms | 3268 | Filli Cafe | no geocoder match for 'Filli Cafe' |
| NO_GEOCODER_MATCH | hms | 3278 | The Port of Peri Peri East Rutherford | no geocoder match for 'The Port of Peri Peri East Rutherford' |
| NO_GEOCODER_MATCH | hms | 3290 | Slappin Chick Hicksville | no geocoder match for 'Slappin Chick Hicksville' |
| NO_GEOCODER_MATCH | hms | 3296 | Urban Crust Pizza & Grill | no geocoder match for 'Urban Crust Pizza & Grill' |
| NO_GEOCODER_MATCH | hms | 3325 | Olive & Garlic Pizzeria | no geocoder match for 'Olive & Garlic Pizzeria' |
| NO_GEOCODER_MATCH | hms | 3327 | Guac Time Mexican Grill (Only this location) | no geocoder match for 'Guac Time Mexican Grill (Only this location)' |
| NO_GEOCODER_MATCH | hms | 3347 | Dhoom Pizza & Grill | no geocoder match for 'Dhoom Pizza & Grill' |
| NO_GEOCODER_MATCH | hms | 3367 | The Port of Peri Peri Astoria | no geocoder match for 'The Port of Peri Peri Astoria' |
| NO_GEOCODER_MATCH | hms | 3370 | Kaizr Indo-Mediterranean Grill | no geocoder match for 'Kaizr Indo-Mediterranean Grill' |
| NO_GEOCODER_MATCH | hms | 3373 | Taco Express Mexican Grill | no geocoder match for 'Taco Express Mexican Grill' |
| NO_GEOCODER_MATCH | hms | 3379 | Filli Cafe Floral Park | no geocoder match for 'Filli Cafe Floral Park' |
| NO_GEOCODER_MATCH | hms | 3380 | Sun Tzu | no geocoder match for 'Sun Tzu' |
| NO_GEOCODER_MATCH | hms | 3400 | Mahi Restaurant & Bakery | no geocoder match for 'Mahi Restaurant & Bakery' |
| NO_GEOCODER_MATCH | hms | 37 | Holy Cow Elmont | no geocoder match for 'Holy Cow Elmont' |
| NO_GEOCODER_MATCH | hms | 43 | Waffle and Kebab | no geocoder match for 'Waffle and Kebab' |
| NO_GEOCODER_MATCH | hms | 46 | Pariwaar Delights | no geocoder match for 'Pariwaar Delights' |
| NO_GEOCODER_MATCH | hms | 47 | Halal Eatz LI | no geocoder match for 'Halal Eatz LI' |
| NO_GEOCODER_MATCH | hms | 476 | Fiesta Healthy Mexican Grill | no geocoder match for 'Fiesta Healthy Mexican Grill' |
| NO_GEOCODER_MATCH | hms | 486 | Holy Cow Oakland Gardens | no geocoder match for 'Holy Cow Oakland Gardens' |
| NO_GEOCODER_MATCH | hms | 491 | Holy Cow Woodland Park | no geocoder match for 'Holy Cow Woodland Park' |
| NO_GEOCODER_MATCH | hms | 51 | Mannan Bakery | no geocoder match for 'Mannan Bakery' |
| NO_GEOCODER_MATCH | hms | 512 | Halal Bros Kabab Grill & Pizza | no geocoder match for 'Halal Bros Kabab Grill & Pizza' |
| NO_GEOCODER_MATCH | hms | 53 | Fire Stone Grill & Pizza | no geocoder match for 'Fire Stone Grill & Pizza' |
| NO_GEOCODER_MATCH | hms | 55 | 804 Halal Platters (Formerly: Pita & Hummus) | no geocoder match for '804 Halal Platters (Formerly: Pita & Hummus)' |
| NO_GEOCODER_MATCH | hms | 56 | Mannan Sweets & Restaurant | no geocoder match for 'Mannan Sweets & Restaurant' |
| NO_GEOCODER_MATCH | hms | 8 | Addys BBQ | no geocoder match for 'Addys BBQ' |
| NO_GEOCODER_MATCH | hms | 9 | Wok & Grill | no geocoder match for 'Wok & Grill' |
| NO_GEOCODER_MATCH | hms | 945 | Hitit Burger | no geocoder match for 'Hitit Burger' |
| NO_GEOCODER_MATCH | hfsaa | R-31D0D5391B | Zabiha Burger & Fries | no geocoder match for 'Zabiha Burger & Fries' |
| NO_GEOCODER_MATCH | hfsaa | R-37E084C4D6 | NYU The Marketplace at Kimmel | no geocoder match for 'NYU The Marketplace at Kimmel' |
| NO_GEOCODER_MATCH | hfsaa | R-83C3FD7B97 | Bangkok Villa Bistro | no geocoder match for 'Bangkok Villa Bistro' |
| NO_GEOCODER_MATCH | hfsaa | R-8D7A98D57F | Zabiha Burger & Fries | no geocoder match for 'Zabiha Burger & Fries' |
| NO_GEOCODER_MATCH | hfsaa | R-979127DBFE | Guac Time Mexican Grill | no geocoder match for 'Guac Time Mexican Grill' |
| NO_GEOCODER_MATCH | hfsaa | R-9C3E205780 | NYU Jasper H. Kane Dining Hall | no geocoder match for 'NYU Jasper H. Kane Dining Hall' |
| NO_GEOCODER_MATCH | hfsaa | R-F9D3D18EA1 | Guac Time Mexican Grill | no geocoder match for 'Guac Time Mexican Grill' |
| WRONG_REGION | hms | 232 | Moon Restaurant | geocoder matched 'Amazing Grace Restaurant' outside Woodside, New York, United States Of America |
| WRONG_REGION | hms | 3038 | Sheikhs N Burgers | geocoder matched 'Sheikh Shack' outside Jamaica, New York, United States Of America |
| WRONG_REGION | hms | 58 | Star Restaurant | geocoder matched 'E&V Restaurant' outside Paterson, New Jersey, United States Of America |
| WRONG_REGION | hfsaa | R-6A0AAEA16F | Kandahar Grill | geocoder matched 'Tikka Charcoal Grill' outside Hicksville, NY, United States |
| WRONG_REGION | hfsaa | R-CEE5734CFF | Gyro King | geocoder matched 'Subway' outside New York, NY, United States |
| WRONG_REGION | hma | 21927 | Dspot Desserts Etobicoke | geocoder matched 'D Spot Dessert Cafe' outside Etobicoke, ON, Canada |
| WRONG_REGION | hma | 22952 | Fortinos Vaughan | geocoder matched 'MAJOR MACKENZIE DR / JANE ST' outside Vaughan, ON, Canada |

## Gaps

- hms: 96 HMS establishments have no directory coordinates; geocoding is required
- hms: 10 HMS establishments have no city in the directory
- hms: 225 row(s) outside every configured metro
- hfsaa: 287 row(s) outside every configured metro
- hma: 8 row(s) outside every configured metro

## Reject reasons

- INVALID_CANDIDATE: 2
- NO_GEOCODER_MATCH: 185
- AMBIGUOUS_GEOCODER_MATCH: 2
- WRONG_REGION: 7
- NOT_A_RESTAURANT: 6
