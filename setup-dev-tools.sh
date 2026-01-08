#!/bin/bash
# QuickStack Labs - Development Tools Setup Script
# Run this to install all development tools

set -e  # Exit on error

echo "🚀 QuickStack Labs - Development Environment Setup"
echo "=================================================="
echo ""

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Check if command exists
command_exists() {
    command -v "$1" >/dev/null 2>&1
}

# Print colored output
print_status() {
    echo -e "${GREEN}✓${NC} $1"
}

print_error() {
    echo -e "${RED}✗${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}⚠${NC} $1"
}

# ============================================
# Check Prerequisites
# ============================================

echo "📋 Checking prerequisites..."
echo ""

if ! command_exists python3; then
    print_error "Python 3 not found. Please install Python 3.10+"
    exit 1
fi
print_status "Python 3 found: $(python3 --version)"

if ! command_exists node; then
    print_warning "Node.js not found. Install from https://nodejs.org/"
else
    print_status "Node.js found: $(node --version)"
fi

if ! command_exists git; then
    print_error "Git not found. Please install Git"
    exit 1
fi
print_status "Git found: $(git --version)"

echo ""

# ============================================
# Python Tools
# ============================================

echo "🐍 Installing Python development tools..."
echo ""

# Check if pipx is available (better than global pip)
if command_exists pipx; then
    print_status "Using pipx for isolated installations"
    pipx install black || print_warning "Black already installed"
    pipx install flake8 || print_warning "Flake8 already installed"
    pipx install isort || print_warning "isort already installed"
    pipx install mypy || print_warning "mypy already installed"
    pipx install pre-commit || print_warning "pre-commit already installed"
else
    print_warning "pipx not found. Installing with pip (not recommended for production)"
    pip3 install --user black flake8 isort mypy pre-commit
fi

print_status "Python tools installed"
echo ""

# ============================================
# Node.js Tools (if Node is available)
# ============================================

if command_exists npm; then
    echo "📦 Installing Node.js development tools..."
    echo ""

    # Check if we should install globally
    read -p "Install Prettier and ESLint globally? (y/n) " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        npm install -g prettier eslint
        print_status "Node.js tools installed globally"
    else
        print_status "Skipping global Node.js tools (will be installed per-project)"
    fi
    echo ""
fi

# ============================================
# Pre-commit Hooks
# ============================================

echo "🔗 Setting up pre-commit hooks..."
echo ""

if [ -f .pre-commit-config.yaml ]; then
    if command_exists pre-commit; then
        pre-commit install
        print_status "Pre-commit hooks installed"

        # Ask if user wants to run hooks now
        read -p "Run pre-commit on all files now? (y/n) " -n 1 -r
        echo
        if [[ $REPLY =~ ^[Yy]$ ]]; then
            pre-commit run --all-files || print_warning "Some checks failed (normal on first run)"
        fi
    else
        print_error "pre-commit not installed. Run: pip install pre-commit"
    fi
else
    print_warning ".pre-commit-config.yaml not found. Skipping."
fi

echo ""

# ============================================
# VS Code Extensions
# ============================================

echo "📝 VS Code Extensions Recommendations..."
echo ""

if command_exists code; then
    print_status "VS Code found"

    # Read extensions from .vscode/extensions.json
    if [ -f .vscode/extensions.json ]; then
        print_status "Extension recommendations configured in .vscode/extensions.json"
        echo "   Open VS Code and install recommended extensions:"
        echo "   Ctrl+Shift+P -> 'Extensions: Show Recommended Extensions'"
    fi
else
    print_warning "VS Code 'code' command not found in PATH"
    echo "   Add VS Code to PATH or open manually"
fi

echo ""

# ============================================
# Git Configuration
# ============================================

echo "🔧 Checking Git configuration..."
echo ""

if [ -z "$(git config --global user.name)" ]; then
    print_warning "Git user.name not set"
    read -p "Enter your name: " git_name
    git config --global user.name "$git_name"
    print_status "Git user.name set to: $git_name"
fi

if [ -z "$(git config --global user.email)" ]; then
    print_warning "Git user.email not set"
    read -p "Enter your email: " git_email
    git config --global user.email "$git_email"
    print_status "Git user.email set to: $git_email"
fi

# Recommended Git settings
git config --global init.defaultBranch main
git config --global pull.rebase true
git config --global core.editor "code --wait"

print_status "Git configured"
echo ""

# ============================================
# Verification
# ============================================

echo "✅ Verifying installation..."
echo ""

# Python tools
for tool in black flake8 isort mypy pre-commit; do
    if command_exists $tool; then
        print_status "$tool: $($tool --version 2>&1 | head -n1)"
    else
        print_error "$tool not found in PATH"
    fi
done

# Node tools
if command_exists npm; then
    if command_exists prettier; then
        print_status "prettier: $(prettier --version)"
    fi
    if command_exists eslint; then
        print_status "eslint: $(eslint --version)"
    fi
fi

echo ""

# ============================================
# Summary
# ============================================

echo "=================================================="
echo "✨ Setup Complete!"
echo "=================================================="
echo ""
echo "📖 Next Steps:"
echo ""
echo "1. Open VS Code:"
echo "   code ."
echo ""
echo "2. Install recommended extensions:"
echo "   Ctrl+Shift+P -> 'Extensions: Show Recommended Extensions'"
echo ""
echo "3. Read the setup guide:"
echo "   cat DEV_SETUP.md"
echo ""
echo "4. Start coding! Try formatting a file:"
echo "   - Open any .py or .ts file"
echo "   - Make some messy code"
echo "   - Save (Ctrl+S)"
echo "   - Watch it auto-format ✨"
echo ""
echo "📚 Documentation:"
echo "   - Architecture: README.md"
echo "   - Structure: STRUCTURE.md"
echo "   - Dev Setup: DEV_SETUP.md"
echo "   - Project Guide: PROJECT_GUIDE.md"
echo ""
echo "Happy coding! 🚀"
