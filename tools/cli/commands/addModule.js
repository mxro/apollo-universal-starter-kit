const shell = require('shelljs');
const fs = require('fs');
const chalk = require('chalk');
const {
  getPackageName,
  getTemplatesPath,
  copyFiles,
  renameFiles,
  computeModulePath,
  getModulesEntryPoint,
  computePackagePath,
  computeModulePackageName,
  addSymlink,
  runPrettier
} = require('../helpers/util');

/**
 * Adds application module to client or server code and adds it to the module list.
 *
 * @param logger - The Logger.
 * @param templatesPath - The path to the templates for a new module.
 * @param moduleName - The name of a new module.
 */
function addModule({ logger, moduleName, module, old }) {
  const packageName = getPackageName(module, old);
  const templatesPath = getTemplatesPath(old);

  copyTemplates();
  mergeWithModules();
  if (!old) addDependency();

  logger.info(chalk.green(`✔ New module ${moduleName} for ${module} successfully created!`));

  /* Add module steps */

  /**
   * Moves templates to newly created module.
   */
  function copyTemplates() {
    logger.info(`Copying ${packageName} files…`);

    // Create new module directory
    const destinationPath = computeModulePath(packageName, old, moduleName);
    const newModule = shell.mkdir('-p', destinationPath);

    // Continue only if directory does not yet exist
    if (newModule.code !== 0) {
      logger.error(chalk.red(`The ${moduleName} directory is already exists.`));
      process.exit();
    }
    // Copy and rename templates in destination directory
    copyFiles(destinationPath, templatesPath, packageName);
    renameFiles(destinationPath, moduleName);

    logger.info(chalk.green(`✔ The ${packageName} files have been copied!`));
  }

  /**
   * Imports module to 'modules.ts' file.
   */
  function mergeWithModules() {
    // Get modules entry point file path
    const modulesEntry = getModulesEntryPoint(module, old);
    let indexContent;

    try {
      // Retrieve the content of the modules.ts
      indexContent =
        `import ${moduleName} from '${computeModulePackageName(moduleName, packageName, old)}';\n` +
        fs.readFileSync(modulesEntry);
    } catch (e) {
      logger.error(chalk.red(`Failed to read ${modulesEntry} file`));
      process.exit();
    }

    // Extract application modules from the modules.ts
    const appModuleRegExp = /Module\(([^()]+)\)/g;
    const [, appModules] = appModuleRegExp.exec(indexContent) || ['', ''];

    // Add a module to app module list
    shell
      .ShellString(indexContent.replace(RegExp(appModuleRegExp, 'g'), `Module(${moduleName}, ${appModules})`))
      .to(modulesEntry);
    runPrettier(modulesEntry);
  }

  function addDependency() {
    // Get package content
    const packagePath = computePackagePath(module);
    const packageContent = `${fs.readFileSync(packagePath)}`;

    // Extract dependencies
    const dependenciesRegExp = /"dependencies":\s\{([^()]+)\},\n\s+"devDependencies"/g;
    const [, dependencies] = dependenciesRegExp.exec(packageContent) || ['', ''];

    // Insert package and sort
    const dependenciesSorted = dependencies.split(',');
    dependenciesSorted.push(`\n    "${computeModulePackageName(moduleName, packageName, old)}": "^1.0.0"`);
    dependenciesSorted.sort();

    // Add module to package list
    shell
      .ShellString(
        packageContent.replace(
          RegExp(dependenciesRegExp, 'g'),
          `"dependencies": {${dependenciesSorted}},\n  "devDependencies"`
        )
      )
      .to(packagePath);

    addSymlink(module, packageName);
  }
}

module.exports = addModule;
