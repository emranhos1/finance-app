# নতুন version release করার নিয়ম

1. `pom.xml`-এ `<version>` বাড়ানো (যেমন `1.1.0` → `1.2.0`)।
2. এই folder-এ `v1.2.0.md` নামে release note — এটাই GitHub Release-এ আর app-এর "What's new?"-এ দেখাবে।
3. কাজটা **`production` branch-এ merge বা push** করুন। ব্যস।

GitHub → **Actions** tab-এ "Release" চলবে (~৫-১০ মিনিট); শেষ হলে **Releases**-এ `DFMS-Update-1.2.0.exe` আসবে আর `v1.2.0` tag নিজে থেকেই তৈরি হবে। এরপর যে PC-তে app খোলা হবে, login screen-এ "New version 1.2.0 is available" আর **Update now** দেখাবে।

- `production`-এ push হলো কিন্তু version আগের মতোই → কিছু release হবে না (ছোট fix-এর জন্য ঠিক আছে)।
- নতুন version কিন্তু `v<version>.md` ফাইল নেই → Actions থেমে যাবে, release হবে না।

## প্রতিটা PC-তে একবার
`credentials.json` (Google Drive backup-এর জন্য) install folder-এ `DFMS.exe`-এর পাশে রেখে দিন। Update এই ফাইল কখনো মুছবে না।
