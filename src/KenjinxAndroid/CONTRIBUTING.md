# Contributing Guide

This repository uses **two active release branches**:

- `kenji-2.0.3` → 2.0.3 line  
- `libryujinx_bionic` → 2.0.4 line  

Other modules (`adrenotools`, `linkernsbypass`, `openal`) stay as they are and should not be modified unless strictly required.

---

## 1. Basic Rules
- Never commit in a **detached HEAD**. Always create a branch.
- Keep your changes **isolated in feature branches**, then merge them into the release branch.
- Sync regularly with **upstream** (original repo) to avoid conflicts.

---

## 2. Workflow

### A) Create a Feature Branch
```bash
git checkout kenji-2.0.3
git pull --rebase origin kenji-2.0.3
git checkout -b fix/shortcut-portrait-2.0.3
# …make changes…
git commit -m "Shortcut: keep portrait during pin dialog (2.0.3)"
git push -u origin fix/shortcut-portrait-2.0.3
```

### B) Merge Back
```bash
git checkout kenji-2.0.3
git pull --rebase origin kenji-2.0.3
git merge --no-ff fix/shortcut-portrait-2.0.3
git push origin kenji-2.0.3
```

### C) Backport to the Other Line
If the change is also needed in 2.0.4:
```bash
git checkout libryujinx_bionic
git pull --rebase origin libryujinx_bionic
git checkout -b fix/shortcut-portrait-2.0.4
git cherry-pick <commit-hash-from-2.0.3>
# resolve conflicts if needed, test…
git push -u origin fix/shortcut-portrait-2.0.4

git checkout libryujinx_bionic
git merge --no-ff fix/shortcut-portrait-2.0.4
git push origin libryujinx_bionic
```

---

## 3. Updating from Upstream
To keep your branches up to date:

```bash
# 2.0.3
git checkout kenji-2.0.3
git fetch upstream
git rebase upstream/kenji-2.0.3
git push origin kenji-2.0.3

# 2.0.4
git checkout libryujinx_bionic
git fetch upstream
git rebase upstream/libryujinx_bionic
git push origin libryujinx_bionic
```

If you work in a team and don’t want to rewrite history:  
Use `git merge upstream/<branch>` instead of `rebase`.

---

## 4. Tags & Releases
For stable builds, tag your commits:

```bash
git checkout kenji-2.0.3
git tag -a v2.0.3-build1 -m "Kenji 2.0.3 Build 1"
git push origin v2.0.3-build1
```

Do the same for 2.0.4.

---

## 5. Android Studio Tips
- Use **New Branch…** (Git menu) instead of committing in detached state.
- Use **Update Project…** (pull --rebase).
- If you must switch branches with uncommitted changes → **Shelve Changes** first, then unshelve later.
- For backports: **Git → Log → Right-click Commit → Cherry-Pick…**

---

## 6. Do’s and Don’ts
✔️ Do create small, isolated branches for each feature/bugfix.  
✔️ Do cherry-pick between release lines instead of merging them together.  
✔️ Do keep `.gitignore` clean (don’t commit build artifacts).  

❌ Don’t work directly in detached HEAD.  
❌ Don’t overwrite submodules/libs (`adrenotools`, `linkernsbypass`, `openal`) unless absolutely required.  
❌ Don’t force-push shared branches without warning.  

---

Happy hacking 🎮
