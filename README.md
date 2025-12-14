# IntelliJ LSP Server Plugin

An IntelliJ IDEA plugin that exposes IntelliJ's powerful code intelligence as a Language Server Protocol (LSP) server. This allows external editors like Neovim, Emacs, and VSCode to leverage IntelliJ's advanced language support for Java, Kotlin, Android, and other JetBrains-supported languages.

## Features

- **🚀 Auto-start**: LSP server automatically starts when a project opens
- **🔌 Multiple Transport Modes**: Choose between TCP (default) or Unix Domain Sockets
- **🎯 Multi-project Support**: Each open project runs its own LSP server instance on a separate port
- **📊 Status Bar Widget**: Real-time display of server status, port, and connected clients
- **⚙️ Configurable**: Customize transport mode and starting port in settings

### Supported LSP Features

- ✅ **Hover** - Documentation and type information on hover
- ✅ **Go to Definition** - Jump to symbol definitions
- ✅ **Go to Type Definition** - Jump to type declarations
- ✅ **Code Completion** - Intelligent code completion
- ✅ **Find References** - Find all references to a symbol
- ✅ **Document Symbols** - Outline view / symbol list
- ✅ **Semantic Tokens** - Rich semantic highlighting
- ✅ **Diagnostics** - Real-time errors and warnings
- ✅ **Document Synchronization** - Incremental document updates

## Installation

### From Source

1. Clone this repository
2. Open in IntelliJ IDEA
3. Run the `Run IDE with Plugin` configuration
4. The plugin will be loaded in a new IntelliJ instance

### Building

```bash
./gradlew buildPlugin
```

The built plugin will be in `build/distributions/IntellijLsp-1.0-SNAPSHOT.zip`

## Configuration

### IntelliJ Settings

1. Go to **Settings → Tools → IntelliJ LSP Server**
2. Configure:
   - **Transport Mode**: TCP (recommended) or Unix Domain Socket
   - **Starting Port**: Default is 2087, will auto-increment if unavailable
   - **Auto-start**: Enable/disable automatic server startup (enabled by default)
3. View server status in the status bar (bottom-right corner)

### Status Bar

The status bar widget displays:

- **LSP: :2087 (1)** - Running on TCP port 2087 with 1 connected client
- **LSP: UDS (0)** - Running on Unix Domain Socket with 0 clients
- **LSP: Not started** - Server hasn't started yet

Hover over the widget to see full connection details.

## Client Configuration

### Neovim

#### Using `vim.lsp.rpc.connect` (Simplest)

Add to your Neovim configuration:

```lua
-- ~/.config/nvim/lua/intellij-lsp.lua
local M = {}

function M.setup()
    -- Connect to IntelliJ LSP server
    -- Change port if your server uses a different one (check status bar in IntelliJ)
    local client = vim.lsp.rpc.connect("127.0.0.1", 2087)

    -- Start the client
    local bufnr = vim.api.nvim_get_current_buf()
    vim.lsp.start({
        name = "intellij-lsp",
        cmd = function()
            return client
        end,
        root_dir = vim.fn.getcwd(),
        on_attach = function(client, bufnr)
            print("IntelliJ LSP attached")
            -- Enable standard keybindings
            vim.keymap.set('n', 'K', vim.lsp.buf.hover, { buffer = bufnr })
            vim.keymap.set('n', 'gd', vim.lsp.buf.definition, { buffer = bufnr })
            vim.keymap.set('n', 'gr', vim.lsp.buf.references, { buffer = bufnr })
            vim.keymap.set('n', '<leader>ca', vim.lsp.buf.code_action, { buffer = bufnr })
        end,
    })
end

return M
```

Then in your `init.lua`:

```lua
require('intellij-lsp').setup()
```

#### Using nvim-lspconfig

```lua
local lspconfig = require('lspconfig')
local configs = require('lspconfig.configs')

-- Define IntelliJ LSP config
if not configs.intellij_lsp then
    configs.intellij_lsp = {
        default_config = {
            cmd = vim.lsp.rpc.connect("127.0.0.1", 2087),
            filetypes = { 'java', 'kotlin', 'groovy', 'xml' },
            root_dir = lspconfig.util.root_pattern('.git', 'build.gradle', 'pom.xml'),
            settings = {},
        },
    }
end

lspconfig.intellij_lsp.setup({
    on_attach = function(client, bufnr)
        -- Your on_attach function
    end,
})
```

#### For Android Projects

```lua
vim.api.nvim_create_autocmd("FileType", {
    pattern = { "java", "kotlin", "xml" },
    callback = function()
        -- Only attach in Android project directories
        local root = vim.fn.finddir('app/src/main', vim.fn.getcwd() .. ';')
        if root ~= '' then
            require('intellij-lsp').setup()
        end
    end,
})
```

### Emacs (lsp-mode)

```elisp
(with-eval-after-load 'lsp-mode
  (lsp-register-client
   (make-lsp-client
    :new-connection (lsp-tcp-connection
                     (lambda (port) `("localhost" ,2087)))
    :major-modes '(java-mode kotlin-mode)
    :server-id 'intellij-lsp)))
```

### VSCode

Create a VSCode extension or use a generic LSP client extension with these settings:

```json
{
  "languageServerExample.port": 2087,
  "languageServerExample.host": "127.0.0.1"
}
```

## Usage Workflow

1. **Open your project in IntelliJ IDEA**

   - The LSP server starts automatically
   - Check the status bar to see the port number

2. **Open the same project in your editor (e.g., Neovim)**

   - Configure your LSP client to connect to the port shown in IntelliJ
   - The connection will be established automatically

3. **Work in your external editor**

   - Use hover, go to definition, completion, etc.
   - IntelliJ provides all the code intelligence

4. **Save files in your external editor**
   - IntelliJ will detect changes and update its analysis
   - Diagnostics will be refreshed automatically

## Important Notes

### Document Synchronization

- **Changes in Neovim → IntelliJ**: Lower priority

  - Changes made in your external editor are tracked by the LSP server
  - However, they are NOT immediately synchronized to IntelliJ's PSI
  - When you save a file, IntelliJ will pick up the changes from disk
  - This means diagnostics and other features work best after saving

- **Changes in IntelliJ → Neovim**: Not supported
  - If you edit in IntelliJ, you'll need to reload the file in your external editor

### Performance

- The LSP server runs inside IntelliJ, so IntelliJ must be running
- Code intelligence is only as good as IntelliJ's current project state
- Make sure IntelliJ has finished indexing before expecting full functionality

### Multiple Projects

- Each open project gets its own LSP server on a different port
- Ports auto-increment from the starting port (default 2087)
- Check the status bar in each IntelliJ project window to see its port
- Connect your editor to the appropriate port for each project

## Troubleshooting

### Server Won't Start

1. Check IntelliJ's `idea.log` for errors
2. Verify the port isn't already in use: `lsof -i :2087` (macOS/Linux)
3. Try changing the starting port in settings
4. Disable and re-enable auto-start

### Client Can't Connect

1. Verify the port in IntelliJ's status bar matches your client configuration
2. Ensure firewall isn't blocking localhost connections
3. Try TCP mode if UDS has issues (or vice versa)
4. Check that IntelliJ has finished starting up

### Features Not Working

1. Make sure IntelliJ has finished indexing the project
2. Save the file to trigger IntelliJ to re-analyze
3. Check that the file type is supported by IntelliJ
4. Verify the client is actually connected (check client count in status bar)

### Performance Issues

1. Reduce the number of completion results (limited to 100 by default)
2. Consider using a faster machine for running IntelliJ
3. Close unused IntelliJ project windows

### Logs

- **IntelliJ Logs**: `Help → Show Log in Finder/Explorer`
- Look for entries with `LspServer`, `JsonRpcHandler`, etc.

## Development

### Project Structure

```
src/main/kotlin/com/frenchef/intellijlsp/
├── config/           # Settings and configuration
├── handlers/         # LSP request handlers
├── intellij/         # IntelliJ integration (PSI, diagnostics, etc.)
├── protocol/         # JSON-RPC and LSP protocol implementation
├── server/           # TCP and UDS server implementations
├── services/         # Project and application services
└── ui/               # Status bar widget
```

### Adding New LSP Features

1. Add the LSP data models in `protocol/models/LspTypes.kt`
2. Create a handler in `handlers/`
3. Register the handler in `LspServerStartupActivity.kt`
4. Update server capabilities in `LifecycleHandler.kt`

### Building and Testing

```bash
# Build the plugin
./gradlew buildPlugin

# Run in test IDE
./gradlew runIde

# Clean build
./gradlew clean build
```

## Future Enhancements

Planned features for future versions:

- [ ] Workspace symbol search
- [ ] Code formatting
- [ ] Code actions (quick fixes)
- [ ] Semantic tokens (better syntax highlighting)
- [ ] Signature help
- [ ] Bidirectional document sync
- [ ] Rename refactoring
- [ ] Call/type hierarchy

## Contributing

Contributions are welcome! Please feel free to submit issues and pull requests.

## License

This project is licensed under the MIT License - see the LICENSE file for details.

## Acknowledgments

- Built with the [IntelliJ Platform SDK](https://plugins.jetbrains.com/docs/intellij/)
- Implements the [Language Server Protocol 3.17](https://microsoft.github.io/language-server-protocol/)
- Inspired by the need for better Android development support in Neovim

## Links

- [Language Server Protocol Specification](https://microsoft.github.io/language-server-protocol/)
- [IntelliJ Platform Plugin SDK](https://plugins.jetbrains.com/docs/intellij/)
- [Neovim LSP Documentation](https://neovim.io/doc/user/lsp.html)
