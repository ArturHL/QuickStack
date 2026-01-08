# QuickStack Labs - Development Environment Setup

## 🚀 Quick Start

### 1. Install VS Code Extensions

Open VS Code and install the recommended extensions:

```bash
# VS Code will prompt you to install recommended extensions
# Or manually: Ctrl+Shift+P -> "Extensions: Show Recommended Extensions"
```

**Essential Extensions:**
- Python (ms-python.python)
- Pylance (ms-python.vscode-pylance)
- Black Formatter (ms-python.black-formatter)
- Prettier (esbenp.prettier-vscode)
- ESLint (dbaeumer.vscode-eslint)
- Error Lens (usernamehw.errorlens) - Highly recommended!

### 2. Install Tools

#### Python Tools

```bash
# Install Python formatters and linters globally
pip install black flake8 isort mypy

# Or using pipx (recommended)
pipx install black
pipx install flake8
pipx install isort
pipx install mypy
```

#### JavaScript/TypeScript Tools

```bash
# Install globally (optional, can be per-project)
npm install -g prettier eslint

# These will be installed per-project in package.json
```

#### Pre-commit Hooks (Optional but Recommended)

```bash
# Install pre-commit
pip install pre-commit

# Install hooks (run from project root)
pre-commit install

# Test hooks
pre-commit run --all-files
```

### 3. Verify Setup

#### Python

```bash
# Test black
echo "x=1" | black -

# Test flake8
flake8 --version

# Test isort
isort --version
```

#### JavaScript

```bash
# Test prettier
prettier --version

# Test eslint
eslint --version
```

---

## 📝 Configuration Files Created

```
QuickStack/
├── .vscode/
│   ├── settings.json          ✅ VS Code settings
│   └── extensions.json        ✅ Recommended extensions
│
├── .prettierrc.json           ✅ Prettier config
├── .prettierignore            ✅ Prettier ignore patterns
├── .eslintrc.json             ✅ ESLint config
├── .flake8                    ✅ Flake8 config
├── .editorconfig              ✅ Editor consistency
├── pyproject.toml             ✅ Python tools config (black, pytest, mypy)
└── .pre-commit-config.yaml    ✅ Pre-commit hooks (optional)
```

---

## 🎨 Code Style Guide

### Python

**Formatter**: Black (120 chars)
**Linter**: Flake8
**Import sorting**: isort

```python
# Good
def calculate_total(items: list[Item], discount: float = 0.0) -> float:
    """Calculate total with optional discount."""
    total = sum(item.price for item in items)
    return total * (1 - discount)


# Bad (Black will auto-fix)
def calculate_total(items:list[Item],discount:float=0.0)->float:
    total=sum(item.price for item in items)
    return total*(1-discount)
```

### JavaScript/TypeScript

**Formatter**: Prettier
**Linter**: ESLint

```typescript
// Good
const calculateTotal = (items: Item[], discount = 0): number => {
  const total = items.reduce((sum, item) => sum + item.price, 0)
  return total * (1 - discount)
}

// Bad (Prettier will auto-fix)
const calculateTotal = (items:Item[],discount=0):number => {
    const total = items.reduce((sum,item)=>sum+item.price,0);
    return total*(1-discount);
};
```

---

## ⚡ Keyboard Shortcuts

### Formatting

- **Format Document**: `Shift + Alt + F` (Windows/Linux) or `Shift + Option + F` (Mac)
- **Format Selection**: `Ctrl + K Ctrl + F` (Windows/Linux) or `Cmd + K Cmd + F` (Mac)
- **Organize Imports**: `Shift + Alt + O` (Windows/Linux) or `Shift + Option + O` (Mac)

### Code Actions

- **Quick Fix**: `Ctrl + .` (Windows/Linux) or `Cmd + .` (Mac)
- **Show All Commands**: `Ctrl + Shift + P` (Windows/Linux) or `Cmd + Shift + P` (Mac)

### Git

- **Open Source Control**: `Ctrl + Shift + G` (Windows/Linux) or `Cmd + Shift + G` (Mac)
- **Commit**: `Ctrl + Enter` (in Source Control panel)

---

## 🔧 Troubleshooting

### Python formatter not working

```bash
# Check Python interpreter
which python3

# Reinstall black
pip uninstall black
pip install black

# Verify in VS Code
# Ctrl+Shift+P -> "Python: Select Interpreter"
```

### ESLint not working

```bash
# Install ESLint in project
cd quickstack-product-template/frontend
npm install --save-dev eslint

# Restart VS Code
```

### Format on save not working

1. Open VS Code settings (Ctrl+,)
2. Search "format on save"
3. Ensure it's checked ✅
4. Check file type is supported in `.vscode/settings.json`

### Pre-commit hooks failing

```bash
# Update hooks
pre-commit autoupdate

# Run manually to see errors
pre-commit run --all-files

# Skip hooks temporarily (not recommended)
git commit --no-verify
```

---

## 📚 Additional Resources

### Python

- [Black Documentation](https://black.readthedocs.io/)
- [Flake8 Rules](https://flake8.pycqa.org/en/latest/user/error-codes.html)
- [isort Documentation](https://pycqa.github.io/isort/)

### JavaScript/TypeScript

- [Prettier Options](https://prettier.io/docs/en/options.html)
- [ESLint Rules](https://eslint.org/docs/latest/rules/)
- [TypeScript Style Guide](https://google.github.io/styleguide/tsguide.html)

### VS Code

- [VS Code Python Tutorial](https://code.visualstudio.com/docs/python/python-tutorial)
- [VS Code JavaScript Tutorial](https://code.visualstudio.com/docs/nodejs/nodejs-tutorial)

---

## 🎯 Tips & Best Practices

### General

1. **Format on save is your friend** - Never manually format code
2. **Trust the linters** - They catch bugs before runtime
3. **Use Error Lens** - See errors inline as you type
4. **Commit often** - Small, focused commits

### Python

1. **Type hints everywhere** - `def func(x: int) -> str:`
2. **Let Black handle formatting** - Don't fight it
3. **Run tests locally** - `pytest` before committing
4. **Use virtual environments** - Always `venv` or `conda`

### JavaScript/TypeScript

1. **Use TypeScript** - Even for small projects
2. **Enable strict mode** - `"strict": true` in tsconfig.json
3. **React hooks dependencies** - Trust ESLint warnings
4. **Use const/let** - Never `var`

### Git

1. **Write good commit messages** - `feat:`, `fix:`, `docs:`, etc.
2. **Review before commit** - Check the diff
3. **Pull before push** - `git pull --rebase origin main`
4. **Branch for features** - `git checkout -b feature/my-feature`

---

## ✅ Verification Checklist

Before starting to code, verify:

- [ ] VS Code opens the project correctly
- [ ] Recommended extensions installed
- [ ] Python formatter works (try formatting a .py file)
- [ ] Prettier works (try formatting a .ts file)
- [ ] ESLint shows errors (try adding `var x = 1;` in .ts file)
- [ ] Error Lens shows inline errors
- [ ] Pre-commit hooks installed (optional)
- [ ] Git configured (`git config --list`)

---

**Ready to code! 🚀**

Open a file and try:
1. Write some messy code
2. Save (Ctrl+S)
3. Watch it auto-format ✨
