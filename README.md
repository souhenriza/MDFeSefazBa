# SefazBa

Extrai as NF-e de entrada interestadual do portal da SEFAZ-BA, verifica a vinculação de MDF-e e gera relatórios em
PDF e Excel com o vencimento da antecipação parcial do ICMS.

O programa abre um menu no terminal, navegado pelas setas:

```
? O que deseja fazer?
❯ Extrair NF-e de uma empresa
  Extrair todas as empresas
  Extrair empresas com intenção comercial
  Retomar lote interrompido
  Ver lotes de extração
  Extrações recentes
  Empresas ▸
  Testar login na SEFAZ
  Abrir pasta de dados e relatórios
  Sair
```

## Instalação

Baixe o instalador da versão mais recente em **[Releases](../../releases)**. Não é preciso instalar Java.

**Windows:** execute `SefazBa-x.y.z.exe`. Não precisa de administrador. Para abrir, use o atalho **SefazBa** no menu
Iniciar. Como o instalador não é assinado digitalmente, o Windows pode mostrar o aviso do SmartScreen. Nesse caso,
clique em *Mais informações* e depois em *Executar assim mesmo*.

**Linux (Ubuntu 24.04 ou mais recente):**

```bash
sudo apt install ./sefazba_x.y.z_amd64.deb
```

Abra pelo atalho **SefazBa** no menu de aplicativos ou digite `sefazba` no terminal. O pacote já instala o Tesseract,
que lê o captcha da consulta.

## Primeiro uso

1. Na primeira execução o programa cria a pasta de dados:
   - Windows: `%APPDATA%\SefazBa`
   - Linux: `~/.local/share/sefazba`
2. **Faça backup de `keys/secret-key.json`** que está nessa pasta. As senhas da SEFAZ são gravadas criptografadas
   com essa chave e, sem ela, não podem ser lidas.
3. Cadastre as empresas em *Empresas ▸ Cadastrar*, ou importe um JSON em *Empresas ▸ Importar*:

   ```json
   [
     {
       "legalName": "EMPRESA EXEMPLO LTDA",
       "tradeName": "EXEMPLO",
       "cnpj": "11222333000181",
       "dominioSistemasId": 10,
       "sefazLogin": "login",
       "sefazPassword": "senha",
       "responsibleUserId": 1,
       "commercialIntention": true,
       "preferOutputPath": null
     }
   ]
   ```

4. Na primeira extração, o navegador de automação é baixado (cerca de 100 MB).

As configurações ficam em `configs.yaml`, na pasta de dados. Ali dá para definir, por exemplo, o nome e o slogan do
escritório que aparecem no PDF e a pasta padrão dos relatórios.

## Uso por linha de comando e agendamento

Quando recebe argumentos, o programa executa um único comando e encerra, sem abrir o menu. É assim que se agenda a
extração mensal:

```bash
sefazba extract-commercial --period LAST_MONTH
sefazba extract-all --period LAST_MONTH --report BOTH --output /caminho/relatorios
sefazba batch resume latest
sefazba --help
```

No Windows, chame o executável instalado, por exemplo no Agendador de Tarefas:
`"%LOCALAPPDATA%\SefazBa\SefazBa.exe" extract-commercial --period LAST_MONTH`.

Dois comandos não rodam extrações ao mesmo tempo. Se um lote for interrompido, por exemplo porque o computador
desligou, ele pode ser retomado depois pelo menu ou com `batch resume`.

## Desenvolvimento

Requisitos: JDK 21. No Linux também é preciso o pacote `tesseract-ocr`.

```bash
./gradlew run                                # abre o menu usando a pasta do projeto como pasta de dados
./gradlew run --args="company list"          # um comando
./gradlew installDist                        # build/install/sefazba/bin/sefazba
./gradlew jpackage                           # instalador do sistema atual (.deb no Linux, .exe no Windows)
```

`SEFAZBA_HOME` muda a pasta de dados. `libs/maven` contém um build modificado do lept4j que não existe no Maven
Central.

### Publicar uma versão

```bash
git tag v1.0.0
git push origin v1.0.0
```

O workflow `.github/workflows/release.yml` gera o `.exe` no Windows e o `.deb` no Linux e anexa os dois à Release.
Para gerar os instaladores sem publicar, execute o workflow manualmente em *Actions ▸ Release ▸ Run workflow*. Os
arquivos ficam disponíveis como artefatos da execução.
