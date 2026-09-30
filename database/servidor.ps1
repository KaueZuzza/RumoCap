<#
    Servidor PostgreSQL exclusivo do RumoCap (porta 5434), separado dos bancos de outros projetos.

    Uso, na pasta do projeto:
        powershell -ExecutionPolicy Bypass -File database\servidor.ps1 criar
        powershell -ExecutionPolicy Bypass -File database\servidor.ps1 iniciar
        powershell -ExecutionPolicy Bypass -File database\servidor.ps1 parar
        powershell -ExecutionPolicy Bypass -File database\servidor.ps1 status

    criar    cria o servidor em %LOCALAPPDATA%\RumoCap (superusuário "postgres" com senha
             aleatória, anotada em superusuario.txt), liga-o, cria o usuário e o banco
             "rumocap" (criar-banco.sql) e coloca um atalho na pasta Inicializar do Windows
             para ligá-lo automaticamente. Use -SemInicioAutomatico para não criar o atalho.
             As senhas do usuário "rumocap" e da área administrativa são geradas e gravadas
             em config\application.properties (arquivo local, fora do Git).
             Pode ser executado novamente: o que já existir é mantido.
    iniciar  liga o servidor em segundo plano (continua ligado ao fechar o terminal).
    parar    desliga o servidor.
    status   informa se o servidor está ligado.
#>
param(
    [Parameter(Position = 0)]
    [ValidateSet("criar", "iniciar", "parar", "status")]
    [string]$Acao = "status",
    [string]$PostgresBin = "C:\Program Files\PostgreSQL\18\bin",
    [int]$Porta = 5434,
    [switch]$SemInicioAutomatico
)

$Pasta = Join-Path $env:LOCALAPPDATA "RumoCap"
$Dados = Join-Path $Pasta "pgdata"
$Log = Join-Path $Pasta "postgres.log"
$ArquivoSenha = Join-Path $Pasta "superusuario.txt"
$Atalho = Join-Path ([Environment]::GetFolderPath("Startup")) "RumoCap - PostgreSQL.lnk"
$ConfigLocal = Join-Path (Split-Path $PSScriptRoot -Parent) "config\application.properties"

$PgCtl = Join-Path $PostgresBin "pg_ctl.exe"
$InitDb = Join-Path $PostgresBin "initdb.exe"
$Psql = Join-Path $PostgresBin "psql.exe"
$PgIsReady = Join-Path $PostgresBin "pg_isready.exe"

function Test-Ligado {
    if (-not (Test-Path (Join-Path $Dados "PG_VERSION"))) { return $false }
    & $PgCtl status -D $Dados | Out-Null
    return $LASTEXITCODE -eq 0
}

function Wait-Conexoes {
    for ($i = 0; $i -lt 30; $i++) {
        & $PgIsReady -h localhost -p $Porta | Out-Null
        if ($LASTEXITCODE -eq 0) { return }
        Start-Sleep -Seconds 1
    }
    throw "O servidor não respondeu na porta $Porta. Consulte $Log e a pasta $Dados\log."
}

function Start-Servidor {
    if (-not (Test-Path (Join-Path $Dados "PG_VERSION"))) {
        throw "O servidor ainda não foi criado. Execute: database\servidor.ps1 criar"
    }
    if (Test-Ligado) {
        Write-Host "O servidor do RumoCap já está ligado (porta $Porta)."
        return
    }
    # Janela oculta: o servidor continua ligado mesmo depois que este terminal for fechado.
    Start-Process -FilePath $PgCtl -ArgumentList "start -D `"$Dados`" -l `"$Log`"" -WindowStyle Hidden
    Wait-Conexoes
    Write-Host "Servidor do RumoCap ligado (localhost, porta $Porta)." -ForegroundColor Green
}

function Stop-Servidor {
    if (-not (Test-Ligado)) {
        Write-Host "O servidor do RumoCap já está desligado."
        return
    }
    & $PgCtl stop -D $Dados -m fast | Out-Null
    Write-Host "Servidor do RumoCap desligado."
}

function New-Senha {
    $caracteres = [char[]]"ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789"
    $bytes = New-Object byte[] 24
    [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
    return -join ($bytes | ForEach-Object { $caracteres[$_ % $caracteres.Length] })
}

# Senha do usuário "rumocap": lida de config\application.properties ou gerada junto com o arquivo
# (que também recebe a senha da área administrativa). O arquivo não vai para o Git.
function Get-SenhaBanco {
    if (Test-Path $ConfigLocal) {
        $linha = Get-Content $ConfigLocal | Where-Object { $_ -like "RUMOCAP_DB_PASSWORD=*" } | Select-Object -First 1
        if ($linha) { return $linha.Substring("RUMOCAP_DB_PASSWORD=".Length).Trim() }
        throw "RUMOCAP_DB_PASSWORD não encontrada em $ConfigLocal."
    }
    $senhaBanco = New-Senha
    New-Item -ItemType Directory -Force -Path (Split-Path $ConfigLocal -Parent) | Out-Null
    Set-Content -Path $ConfigLocal -Encoding ASCII -Value @(
        "# Configuracao local do RumoCap, gerada por database\servidor.ps1."
        "# Este arquivo NAO vai para o Git. Variaveis de ambiente com o mesmo nome tem prioridade."
        "RUMOCAP_DB_PASSWORD=$senhaBanco"
        "RUMOCAP_ADMIN_USERNAME=admin"
        "RUMOCAP_ADMIN_PASSWORD=$(New-Senha)"
    )
    Write-Host "Senhas do banco e da administração gravadas em $ConfigLocal." -ForegroundColor Green
    return $senhaBanco
}

function New-Servidor {
    if (-not (Test-Path $InitDb)) {
        throw "PostgreSQL não encontrado em '$PostgresBin'. Informe a pasta bin com -PostgresBin."
    }

    if (Test-Path (Join-Path $Dados "PG_VERSION")) {
        Write-Host "O servidor já existe em $Dados."
    } else {
        if (Get-NetTCPConnection -LocalPort $Porta -State Listen -ErrorAction SilentlyContinue) {
            throw "A porta $Porta já está em uso. Escolha outra com -Porta (e ajuste RUMOCAP_DB_URL)."
        }
        New-Item -ItemType Directory -Force -Path $Pasta | Out-Null

        $senha = New-Senha
        $arquivoTemporario = Join-Path $Pasta "senha.tmp"
        [IO.File]::WriteAllText($arquivoTemporario, $senha)
        try {
            & $InitDb -D $Dados -U postgres "--pwfile=$arquivoTemporario" -E UTF8 -A scram-sha-256
            if ($LASTEXITCODE -ne 0) { throw "O initdb não conseguiu criar o servidor." }
        } finally {
            Remove-Item $arquivoTemporario -ErrorAction SilentlyContinue
        }

        Set-Content -Path $ArquivoSenha -Encoding UTF8 -Value @(
            "Servidor PostgreSQL exclusivo do RumoCap"
            "Endereço: localhost, porta $Porta"
            "Superusuário: postgres"
            "Senha: $senha"
            ""
            "O site usa o usuário 'rumocap', dono do banco 'rumocap' (senha em config\application.properties do projeto)."
        )

        # Porta própria, acesso somente deste computador e logs com rotação semanal.
        Add-Content -Path (Join-Path $Dados "postgresql.conf") -Encoding ASCII -Value @(
            ""
            "# ---- RumoCap ----"
            "port = $Porta"
            "listen_addresses = 'localhost'"
            "logging_collector = on"
            "log_filename = 'postgresql-%a.log'"
            "log_truncate_on_rotation = on"
            "log_rotation_age = 1d"
            "log_rotation_size = 0"
        )
        Write-Host "Servidor criado em $Dados." -ForegroundColor Green
    }

    Start-Servidor

    $senhaBanco = Get-SenhaBanco
    $linhaSenha =Get-Content $ArquivoSenha | Where-Object { $_ -like "Senha: *" } | Select-Object -First 1
    $env:PGPASSWORD = $linhaSenha.Substring("Senha: ".Length)
    $env:PGCLIENTENCODING = "UTF8"
    try {
        & $Psql -h localhost -p $Porta -U postgres -d postgres -q -v ON_ERROR_STOP=1 -v "senha_rumocap=$senhaBanco" -f(Join-Path $PSScriptRoot "criar-banco.sql")
        if ($LASTEXITCODE -ne 0) { throw "Não foi possível criar o usuário e o banco 'rumocap'." }
    } finally {
        Remove-Item Env:\PGPASSWORD -ErrorAction SilentlyContinue
    }
    Write-Host "Usuário e banco 'rumocap' prontos." -ForegroundColor Green

    if ($SemInicioAutomatico) {
        Write-Host "Atalho de inicialização não criado. Ligue o servidor com: database\servidor.ps1 iniciar"
    } else {
        New-AtalhoInicializacao
    }
}

# Atalho na pasta Inicializar: liga o servidor quando o usuário entra no Windows.
function New-AtalhoInicializacao {
    $comando = "Start-Process -WindowStyle Hidden -FilePath '$PgCtl' " +
               "-ArgumentList 'start -D \`"$Dados\`" -l \`"$Log\`"'"
    $shell = New-Object -ComObject WScript.Shell
    $link = $shell.CreateShortcut($Atalho)
    $link.TargetPath = Join-Path $PSHOME "powershell.exe"
    $link.Arguments = "-NoProfile -WindowStyle Hidden -Command `"$comando`""
    $link.WorkingDirectory = $Pasta
    $link.WindowStyle = 7
    $link.Description = "Liga o servidor PostgreSQL do RumoCap (porta $Porta)"
    $link.Save()
    Write-Host "Início automático configurado: $Atalho"
}

try {
    switch ($Acao) {
        "criar" { New-Servidor }
        "iniciar" { Start-Servidor }
        "parar" { Stop-Servidor }
        "status" {
            if (Test-Ligado) {
                Write-Host "Servidor do RumoCap ligado (localhost, porta $Porta). Dados em $Dados."
            } else {
                Write-Host "Servidor do RumoCap desligado. Ligue com: database\servidor.ps1 iniciar"
            }
        }
    }
} catch {
    Write-Host $_.Exception.Message -ForegroundColor Red
    exit 1
}
