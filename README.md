<img src="https://raw.githubusercontent.com/Checkmarx/ci-cd-integrations/main/.images/PluginBanner.jpg">
<br />
<div align="center">

[![Contributors][contributors-shield]][contributors-url]
[![Forks][forks-shield]][forks-url]
[![Stargazers][stars-shield]][stars-url]
[![Issues][issues-shield]][issues-url]
[![Install][install-shield]][install-url]
[![Apache License][license-shield]][license-url]


</div>
<!-- PROJECT LOGO -->
<br />
<p align="center">
  <a href="https://github.com/Checkmarx/ast-eclipse-plugin">
    <img src="https://raw.githubusercontent.com/Checkmarx/ci-cd-integrations/main/.images/cx_azure_x-icon-80px.png" alt="Logo" width="80" height="80" />
  </a>

  <h3 align="center">CHECKMARX ONE ECLIPSE PLUGIN</h3>

  <p align="center">
    The Checkmarx One Eclipse plugin enables you to import results from a Checkmarx One scan directly into your IDE.
    <br />
    <a href="https://checkmarx.com/resource/documents/en/34965-68728-checkmarx-one-eclipse-plugin.html"><strong>Explore the docs »</strong></a>
    <br />
    <a href="https://marketplace.eclipse.org/content/checkmarx-ast-plugin"><strong>Marketplace »</strong></a>
  </p>
</p>


<!-- TABLE OF CONTENTS -->
<details>
  <summary>Table of Contents</summary>
  <ol>
    <li>
      <a href="#overview">Overview</a>
      <ul>
        <li>
          <a href="#checkmarx-one-platform">Checkmarx One Platform</a>
          <ul>
            <li><a href="#key-features">Key Features</a></li>
            <li><a href="#prerequisites">Prerequisites</a></li>
            <li><a href="#initial-setup">Initial Setup</a></li>
            <li><a href="#usage">Usage</a></li>
          </ul>
        </li>
        <li>
          <a href="#checkmarx-developer-assist">Checkmarx Developer Assist</a>
          <ul>
            <li><a href="#key-features-1">Key Features</a></li>
            <li><a href="#prerequisites-1">Prerequisites</a></li>
            <li><a href="#initial-setup-1">Initial Setup</a></li>
            <li><a href="#usage-1">Usage</a></li>
          </ul>
        </li>
      </ul>
    </li>
    <li><a href="#contribution">Contribution</a></li>
    <li><a href="#license">License</a></li>
    <li><a href="#contact">Contact</a></li>
  </ol>
</details>



<!-- Overview -->
# Overview

Checkmarx continues to spearhead the shift-left approach to AppSec by bringing our powerful AppSec tools into your IDE. This empowers developers to identify vulnerabilities and remediate them **as they code**. The Checkmarx One Eclipse plugin integrates seamlessly into your IDE, enabling you to access the full functionality of your Checkmarx One account (SAST, SCA, IaC, and Secret Detection) directly from your IDE.
This plugin contains two separate capabilities:

-   Checkmarx One Platform

-   Checkmarx Developer Assist  

## Checkmarx One Platform
This tool enables Checkmarx One users to access the full functionality of your Checkmarx One account (SAST, SCA, IaC, and Secret Detection) directly from your IDE. You can run new scans, or import results from scans run in your Checkmarx One account. Checkmarx provides detailed info about each vulnerability, including remediation recommendations and examples of effective remediation. The plugin enables you to navigate from a vulnerability to the relevant source code, so that you can easily zero-in on the problematic code and start working on remediation.

### Key Features
* Access the full power of Checkmarx One (SAST, SCA, IaC, and Secret Detection) directly from your IDE 
* Run a new scan from your IDE even before committing the code, or import scan results from your Checkmarx One account
* Provides actionable results including remediation recommendations. Navigate from results panel directly to the highlighted vulnerable code in the editor and get right down to work on the remediation.
* Group and filter results
* Triage results (by adjusting the severity and state and adding comments) directly from the Eclipse console (currently supported for SAST and IaC Security)
* Links to Codebashing lessons

### Prerequisites

- An Eclipse installation, version 2020-09 or above. 
   > Supported platforms: Windows, Mac, Linux/GTK

- You have an **API key** for your Checkmarx One account. To create an
  API key, see [Generating an API Key](https://checkmarx.com/resource/documents/en/34965-68618-generating-an-api-key.html)
  > In order to use this integration for running an end-to-end flow of scanning a project and viewing results with the minimum required permissions, the API Key or user account should have the role `plugin-scanner`. Alternatively, they can have at a minimum the out-of-the-box composite role `ast-scanner` as well as the IAM role `default-roles`.
   

### Initial Setup

1.   Verify that all prerequisites are in place.

2.   Install the **Checkmarx One** plugin and configure the settings as described [here](https://checkmarx.com/resource/documents/en/34965-68729-installing-and-setting-up-the-checkmarx-one-eclipse-plugin.html).

### Usage

To see how you can use our tool, please refer to the [Documentation](https://docs.checkmarx.com/en/34965-68731-using-the-checkmarx-one-eclipse-plugin.html).


## Checkmarx Developer Assist
Developer Assist is an agentic AI tool that delivers real-time context-aware prevention, remediation, and guidance to developers inside the IDE. 

### Key Features

* An advanced security agent that delivers real-time context-aware prevention, remediation, and guidance to developers from the IDE.​
* Real-time scanners identify risks as you code.
* **ASCA**, a lightweight source code scanner, enables developers to identify secure coding best practice violations in the file that they are working on as they code.
* Specialized real-time scanners identify vulnerable open source packages and container images, as well as exposed secrets and IaC risks.
* MCP-based agentic AI remediation.
* AI-powered explanation of risk details.
* Reduce noise by marking false positives as ignored.


### Prerequisites

  - Eclipse installation, version 2025-06 and above with GitHub Copilot
  - A Checkmarx One account with a **Checkmarx One Assist** license. Also, **Dev Assist** must be activated for your tenant account in the Checkmarx One UI under **Global Settings > Plugins** page. This must be done by an account admin.  
You will need to provide an **API key** for your Checkmarx One account. To create an API key, see [Generating an API Key](https://checkmarx.com/resource/documents/en/34965-68618-generating-an-api-key.html)
    

### Initial Setup

1.   Verify that all prerequisites are in place.

2.   Install the **Checkmarx One** plugin and configure the settings as described [here](https://checkmarx.com/resource/documents/en/34965-68729-installing-and-setting-up-the-checkmarx-one-eclipse-plugin.html).
3. After authentication, in the welcome screen, select the checkbox next to "Code Smarter with Checkmarx One Assist​".

### Usage

To see how you can use our tool, please refer to the [Documentation](https://docs.checkmarx.com/en/34965-68731-using-the-checkmarx-one-eclipse-plugin.html).

**GIF - AI Remediation with Developer Assist**
<PLACEHOLDER>


## Contribution

We appreciate feedback and contribution to the ECLIPSE PLUGIN! Before you get started, please see the following:

- [Checkmarx contribution guidelines](docs/contributing.md)
- [Checkmarx Code of Conduct](docs/code_of_conduct.md)


## License
Distributed under the [Apache 2.0](LICENSE). See `LICENSE` for more information.

## Contact

Checkmarx - Integrations Team

Project Link: [https://github.com/Checkmarx/ast-eclipse-plugin](https://github.com/Checkmarx/ast-eclipse-plugin)

Find more integrations from our team [here](https://github.com/Checkmarx/ci-cd-integrations#checkmarx-ast-integrations)

© 2026 Checkmarx Ltd. All Rights Reserved.

[contributors-shield]: https://img.shields.io/github/contributors/Checkmarx/ast-eclipse-plugin.svg
[contributors-url]: https://github.com/Checkmarx/ast-eclipse-plugin/graphs/contributors
[forks-shield]: https://img.shields.io/github/forks/Checkmarx/ast-eclipse-plugin.svg
[forks-url]: https://github.com/Checkmarx/ast-eclipse-plugin/network/members
[stars-shield]: https://img.shields.io/github/stars/Checkmarx/ast-eclipse-plugin.svg
[stars-url]: https://github.com/Checkmarx/ast-eclipse-plugin/stargazers
[issues-shield]: https://img.shields.io/github/issues/Checkmarx/ast-eclipse-plugin.svg
[issues-url]: https://github.com/Checkmarx/ast-eclipse-plugin/issues
[license-shield]: https://img.shields.io/github/license/Checkmarx/ast-eclipse-plugin.svg
[license-url]: https://github.com/Checkmarx/ast-eclipse-plugin/blob/main/LICENSE
[install-shield]: https://img.shields.io/eclipse-marketplace/dt/checkmarx-ast-plugin
[install-url]: https://marketplace.eclipse.org/content/checkmarx-ast-plugin
