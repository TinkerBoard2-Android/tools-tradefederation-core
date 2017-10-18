#
# Copyright 2017, The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

"""
Command Line Translator for atest.
"""

import itertools
import json
import logging
import os
import re
import subprocess
import time
import xml.etree.ElementTree as ET
from collections import namedtuple


RUN_CMD = ('atest_tradefed.sh run commandAndExit template/local_min '
           '--template:map test=atest %s')
MODULES_IN = 'MODULES-IN-%s'
MODULE_CONFIG = 'AndroidTest.xml'
TF_TARGETS = frozenset(['tradefed', 'tradefed-contrib'])
GTF_TARGETS = frozenset(['google-tradefed', 'google-tradefed-contrib'])

# Helps find apk files listed in a test config (AndroidTest.xml) file.
# Matches "filename.apk" in <option name="foo", value="bar/filename.apk" />
APK_RE = re.compile(r'^[^/]+\.apk$', re.I)
# Find integration name based on file path of integration config xml file.
# Group matches "foo/bar" given "blah/res/config/blah/res/config/foo/bar.xml
INT_NAME_RE = re.compile(r'^.*\/res\/config\/(?P<int_name>.*).xml$')
# Parse package name from the package declaration line of a java file.
# Group matches "foo.bar" of line "package foo.bar;"
PACKAGE_RE = re.compile(r'\s*package\s+(?P<package>[^;]+)\s*;\s*', re.I)

class TooManyTestsFoundError(Exception):
    """Raised when unix find command finds too many tests."""

class NoTestFoundError(Exception):
    """Raised when no tests are found."""

class TestWithNoModuleError(Exception):
    """Raised when test files have no parent module directory."""

class UnregisteredModuleError(Exception):
    """Raised when module is not in module-info.json."""

class MissingPackageName(Exception):
    """Raised when the test class java file does not contain a package name."""

class Enum(tuple):
    """enum library isn't a Python 2.7 built-in, so roll our own."""
    __getattr__ = tuple.index

# Explanation of REFERENCE_TYPEs:
# ----------------------------------
# 0. MODULE: LOCAL_MODULE or LOCAL_PACKAGE_NAME value in Android.mk/Android.bp.
# 1. MODULE_CLASS: Combo of MODULE and CLASS as "module:class".
# 2. PACKAGE: package in java file. Same as file path to java file.
# 3. MODULE_PACKAGE: Combo of MODULE and PACKAGE as "module:package".
# 4. FILE_PATH: file path to dir of tests or test itself.
# 5. INTEGRATION: xml file name in one of the 4 integration config directories.
# 6. SUITE: Value of the "run-suite-tag" in xml config file in 4 config dirs.
#           Same as value of "test-suite-tag" in AndroidTest.xml files.
REFERENCE_TYPE = Enum(['MODULE', 'CLASS', 'QUALIFIED_CLASS', 'MODULE_CLASS',
                       'PACKAGE', 'MODULE_PACKAGE', 'FILE_PATH', 'INTEGRATION',
                       'SUITE'])

# Unix find commands for searching for test files based on test type input.
# Note: Find (unlike grep) exits with status 0 if nothing found.
FIND_CMDS = {
    REFERENCE_TYPE.CLASS : r"find %s -type d -name .git -prune -o -type f "
                           r"-name '%s.java' -print",
    REFERENCE_TYPE.QUALIFIED_CLASS: r"find %s -type d -name .git -prune -o "
                                    r"-wholename '*%s.java' -print",
    REFERENCE_TYPE.INTEGRATION: r"find %s -type d -name .git -prune -o "
                                r"-wholename '*%s.xml' -print"
}

TestInfoBase = namedtuple('TestInfo', ['rel_config', 'module_name',
                                       'integrated_name', 'filters'])
class TestInfo(TestInfoBase):
    """Information needed to identify and run a test."""

    def paramify(self):
        """Return string suitable for --test-info tradefed param."""
        if self.integrated_name:
            return self.integrated_name
        # TODO(b/68205177): Support method filters.
        if self.filters:
            filter_str = ','.join(self.filters)
            return '%s:%s' % (self.module_name, filter_str)
        return self.module_name

#pylint: disable=no-self-use
class CLITranslator(object):
    """
    CLITranslator class contains public method translate() and some private
    helper methods. The atest tool can call the translate() method with a list
    of strings, each string referencing a test to run. Translate() will
    "translate" this list of test strings into a list of build targets and a
    list of TradeFederation run commands.

    Translation steps for a test string reference:
        1. Narrow down the type of reference the test string could be, i.e.
           whether it could be referencing a Module, Class, Package, etc.
        2. Try to find the test files assuming the test string is one of these
           types of reference.
        3. If test files found, generate Build Targets and the Run Command.
    """

    def __init__(self, root_dir='/'):
        if not os.path.isdir(root_dir):
            raise ValueError('%s is not valid dir.' % root_dir)
        self.root_dir = os.path.realpath(root_dir)
        self.out_dir = os.environ.get('OUT')
        self.ref_type_to_func_map = {
            REFERENCE_TYPE.MODULE: self._find_test_by_module_name,
            REFERENCE_TYPE.CLASS: self._find_test_by_class_name,
            REFERENCE_TYPE.QUALIFIED_CLASS: self._find_test_by_class_name,
            REFERENCE_TYPE.FILE_PATH: self._find_test_by_path,
            REFERENCE_TYPE.INTEGRATION: self._find_test_by_integration_name,
        }
        self.module_info = self._load_module_info()
        self.tf_dirs, self.gtf_dirs = self._get_integration_dirs()
        self.integration_dirs = self.tf_dirs + self.gtf_dirs

    def _load_module_info(self):
        """Make (if not exists) and load into memory module-info.json file

        Returns:
             A dict of data about module names and dir locations.
        """
        file_path = os.path.join(self.out_dir, 'module-info.json')
        # Make target is simply file path relative to root.
        make_target = os.path.relpath(file_path, self.root_dir)
        if not os.path.isfile(file_path):
            logging.info('Generating module-info.json - this is required for '
                         'initial runs.')
            cmd = ['make', '-j', '-C', self.root_dir, make_target]
            logging.debug('Executing: %s', cmd)
            subprocess.check_output(cmd, stderr=subprocess.STDOUT)
        with open(file_path) as json_file:
            return json.load(json_file)

    def _get_integration_dirs(self):
        """Get integration dirs from module-info.json based on targets.

        Returns:
            A tuple of lists of strings of integration dir rel to repo root.
        """
        tf_dirs = filter(None, [self._get_module_path(x) for x in TF_TARGETS])
        gtf_dirs = filter(None, [self._get_module_path(x) for x in GTF_TARGETS])
        return tf_dirs, gtf_dirs

    def _get_test_reference_types(self, ref):
        """Determine type of test reference based on the content of string.

        Examples:
            The string 'SequentialRWTest' could be a reference to
            a Module or a Class name.

            The string 'cts/tests/filesystem' could be a Path, Integration
            or Suite reference.

        Args:
            ref: A string referencing a test.

        Returns:
            A list of possible REFERENCE_TYPEs (ints) for reference string.
        """
        if ref.startswith('.'):
            return [REFERENCE_TYPE.FILE_PATH]
        if '/' in ref:
            if ref.startswith('/') or '.' in ref:
                return [REFERENCE_TYPE.FILE_PATH]
            return [REFERENCE_TYPE.FILE_PATH,
                    REFERENCE_TYPE.INTEGRATION,
                    # Comment in SUITE when it's supported
                    # REFERENCE_TYPE.SUITE
                   ]
        if ':' in ref:
            if '.' in ref:
                return [REFERENCE_TYPE.MODULE_CLASS,
                        REFERENCE_TYPE.MODULE_PACKAGE]
            return [REFERENCE_TYPE.MODULE_CLASS]
        if '.' in ref:
            return [REFERENCE_TYPE.FILE_PATH,
                    REFERENCE_TYPE.QUALIFIED_CLASS,
                    REFERENCE_TYPE.PACKAGE]
        # Note: We assume that if you're referencing a file in your cwd,
        # that file must have a '.' in its name, i.e. foo.java, foo.xml.
        # If this ever becomes not the case, then we need to include path below.
        return [REFERENCE_TYPE.INTEGRATION,
                # Comment in SUITE when it's supported
                # REFERENCE_TYPE.SUITE,
                REFERENCE_TYPE.MODULE, REFERENCE_TYPE.CLASS]

    def _is_equal_or_sub_dir(self, sub_dir, parent_dir):
        """Return True sub_dir is sub dir or equal to parent_dir.

        Args:
          sub_dir: A string of the sub directory path.
          parent_dir: A string of the parent directory path.

        Returns:
            A boolean of whether both are dirs and sub_dir is sub of parent_dir
            or is equal to parent_dir.
        """
        # avoid symlink issues with real path
        parent_dir = os.path.realpath(parent_dir)
        sub_dir = os.path.realpath(sub_dir)
        if not os.path.isdir(sub_dir) or not os.path.isdir(parent_dir):
            return False
        return os.path.commonprefix([sub_dir, parent_dir]) == parent_dir

    def _find_parent_module_dir(self, start_dir):
        """From current dir search up file tree until root dir for module dir.

        Args:
          start_dir: A string of the dir to start searching up from.

        Returns:
            A string of the module dir relative to root.

        Exceptions:
            ValueError: Raised if cur_dir not dir or not subdir of root dir.
            TestWithNoModuleError: Raised if no Module Dir found.
        """
        if not self._is_equal_or_sub_dir(start_dir, self.root_dir):
            raise ValueError('%s not in repo %s' % (start_dir, self.root_dir))
        current_dir = start_dir
        while current_dir != self.root_dir:
            if os.path.isfile(os.path.join(current_dir, MODULE_CONFIG)):
                return os.path.relpath(current_dir, self.root_dir)
            current_dir = os.path.dirname(current_dir)
        raise TestWithNoModuleError('No Parent Module Dir for: %s' % start_dir)

    def _extract_test_path(self, output):
        """Extract the test path from the output of a unix 'find' command.

        Example of find output for CLASS find cmd:
        /<some_root>/cts/tests/jank/src/android/jank/cts/ui/CtsDeviceJankUi.java

        Args:
            output: A string output of a unix 'find' command.

        Returns:
            A string of the test path or None if output is '' or None.
        """
        if not output:
            return None
        tests = output.strip('\n').split('\n')
        count = len(tests)
        test_index = 0
        if count > 1:
            numbered_list = ['%s: %s' % (i, t) for i, t in enumerate(tests)]
            print 'Multiple tests found:\n%s' % '\n'.join(numbered_list)
            test_index = int(raw_input("Please enter number of test to use:"))
        return tests[test_index]

    def _get_module_name(self, rel_module_path):
        """Get the name of a module given its dir relative to repo root.

        Example of module_info.json line:

        'AmSlam':
        {
        'class': ['APPS'],
        'path': ['frameworks/base/tests/AmSlam'],
        'tags': ['tests'],
        'installed': ['out/target/product/bullhead/data/app/AmSlam/AmSlam.apk']
        }

        Args:
            rel_module_path: A string of module's dir relative to repo root.

        Returns:
            A string of the module name, else None if not found.

        Exceptions:
            UnregisteredModuleError: Raised if module not in module-info.json.
        """
        for name, info in self.module_info.iteritems():
            if (rel_module_path == info.get('path', [])[0] and
                    info.get('installed')):
                return name
        raise UnregisteredModuleError('%s not in module-info.json' %
                                      rel_module_path)

    def _get_module_path(self, module_name):
        """Get path from module-info.json given a module name.

        Args:
            module_name: A string of the module name.

        Returns:
            A string of path to the module, else None if no module found.
        """
        info = self.module_info.get(module_name)
        if info:
            return info.get('path', [])[0]
        return None

    def _get_targets_from_xml(self, xml_file):
        """Parse any .apk files listed in the config file.

        Args:
            xml_file: abs path to xml file.

        Returns:
            A set of build targets based on the .apks found in the xml file.
        """
        targets = set()
        tree = ET.parse(xml_file)
        root = tree.getroot()
        option_tags = root.findall('.//option')
        for tag in option_tags:
            value = tag.attrib['value'].strip()
            if APK_RE.match(value):
                targets.add(value[:-len('.apk')])
        logging.debug('Targets found in config file: %s', targets)
        return targets

    def _get_fully_qualified_class_name(self, test_path):
        """Parse the fully qualified name from the class java file.

        Args:
            test_path: A string of absolute path to the java class file.

        Returns:
            A string of the fully qualified class name.
        """
        with open(test_path) as class_file:
            for line in class_file:
                match = PACKAGE_RE.match(line)
                if match:
                    package = match.group('package')
                    cls = os.path.splitext(os.path.split(test_path)[1])[0]
                    return '%s.%s' % (package, cls)
        raise MissingPackageName(test_path)

    def _find_test_by_module_name(self, module_name):
        """Find test files given a module name.

        Args:
            module_name: A string of the test's module name.

        Returns:
            A populated TestInfo namedtuple if found, else None.
        """
        info = self.module_info.get(module_name)
        if info and info.get('installed'):
            # path is a list with only 1 element.
            rel_config = os.path.join(info['path'][0], MODULE_CONFIG)
            return TestInfo(rel_config, module_name, None, None)

    def _find_test_by_class_name(self, class_name):
        """Find test files given a class name.

        Args:
            class_name: A string of the test's class name.

        Returns:
            A populated TestInfo namedtuple if test found, else None.
        """
        if '.' in class_name:
            find_cmd = FIND_CMDS[REFERENCE_TYPE.QUALIFIED_CLASS] % (
                self.root_dir, class_name.replace('.', '/'))
        else:
            find_cmd = FIND_CMDS[REFERENCE_TYPE.CLASS] % (
                self.root_dir, class_name)
        # TODO: Pull out common find cmd and timing code.
        start = time.time()
        logging.debug('Executing: %s', find_cmd)
        out = subprocess.check_output(find_cmd, shell=True)
        logging.debug('Find completed in %ss', time.time() - start)
        logging.debug('Class - Find Cmd Out: %s', out)
        test_path = self._extract_test_path(out)
        full_class_name = self._get_fully_qualified_class_name(test_path)
        if not test_path:
            return None
        test_dir = os.path.dirname(test_path)
        rel_module_dir = self._find_parent_module_dir(test_dir)
        rel_config = os.path.join(rel_module_dir, MODULE_CONFIG)
        module_name = self._get_module_name(rel_module_dir)
        return TestInfo(rel_config, module_name, None, {full_class_name})

    def _find_test_by_integration_name(self, name):
        """Find test info given an integration name.

        Args:
            name: A string of integration name as seen in tf's list configs.

        Returns:
            A populated TestInfo namedtuple if test found, else None
        """
        for integration_dir in self.integration_dirs:
            abs_path = os.path.join(self.root_dir, integration_dir)
            find_cmd = FIND_CMDS[REFERENCE_TYPE.INTEGRATION] % (abs_path, name)
            logging.debug('Executing: %s', find_cmd)
            out = subprocess.check_output(find_cmd, shell=True)
            logging.debug('Integration - Find Cmd Out: %s', out)
            test_file = self._extract_test_path(out)
            if test_file:
                # Don't use names that simply match the path,
                # must be the actual name used by TF to run the test.
                match = INT_NAME_RE.match(test_file)
                if not match:
                    logging.error('Integration test outside config dir: %s',
                                  test_file)
                    return None
                int_name = match.group('int_name')
                if int_name != name:
                    logging.warn('Input (%s) not valid integration name, '
                                 'did you mean: %s?', name, int_name)
                    return None
                rel_config = os.path.relpath(test_file, self.root_dir)
                return TestInfo(rel_config, None, name, None)
        return None

    def _find_test_by_path(self, path):
        """Find test info given a path.

        Strategy:
            path_to_java_file --> Resolve to CLASS (TODO: Class just runs module
                                                    at the moment though)
            path_to_module_dir -> Resolve to MODULE
            path_to_class_dir --> Resolve to MODULE (TODO: Maybe all classes)
            path_to_integration_file --> Resolve to INTEGRATION
            path_to_random_dir --> try to resolve to MODULE
            # TODO:
            path_to_dir_with_integration_files --> Resolve to ALL Integrations

        Args:
            path: A string of the test's path.

        Returns:
            A populated TestInfo namedtuple if test found, else None
        """
        # TODO: See if this can be generalized and shared with methods above.
        # create absolute path from cwd and remove symbolic links
        path = os.path.realpath(path)
        if not os.path.exists(path):
            return None
        if os.path.isfile(path):
            dir_path, file_name = os.path.split(path)
        else:
            dir_path, file_name = path, None

        # Integration/Suite
        int_dir = None
        for possible_dir in self.integration_dirs:
            abs_int_dir = os.path.join(self.root_dir, possible_dir)
            if self._is_equal_or_sub_dir(dir_path, abs_int_dir):
                int_dir = abs_int_dir
                break
        if int_dir:
            if not file_name:
                logging.warn('Found dir (%s) matching input (%s).'
                             ' Referencing an entire Integration/Suite dir'
                             ' is not supported. If you are trying to reference'
                             ' a test by its path, please input the path to'
                             ' the integration/suite config file itself.'
                             ' Continuing to try to resolve input (%s)'
                             ' as a non-path reference...',
                             int_dir, path, path)
                return None
            rel_config = os.path.relpath(path, self.root_dir)
            match = INT_NAME_RE.match(rel_config)
            if not match:
                logging.error('Integration test outside config dir: %s',
                              rel_config)
                return None
            int_name = match.group('int_name')
            return TestInfo(rel_config, None, int_name, None)

        # Module/Class
        rel_module_dir = self._find_parent_module_dir(dir_path)
        if not rel_module_dir:
            return None
        module_name = self._get_module_name(rel_module_dir)
        rel_config = os.path.join(rel_module_dir, MODULE_CONFIG)
        class_name = None
        if file_name and file_name.endswith('.java'):
            class_name = self._get_fully_qualified_class_name(path)
        return TestInfo(rel_config, module_name, None,
                        {class_name} if class_name else None)

    def _flatten_by_module(self, test_infos):
        """Flatten a list of TestInfos so only one TestInfo per module in list.

        Args:
            test_infos: A list of TestInfo namedtuples.

        Returns:
            A list of TestInfo namedtuples flattened by module.
        """
        result = []
        group_func = lambda x: x.module_name
        for module, group in itertools.groupby(test_infos, group_func):
            # module is a string, group is a generator of grouped TestInfos.
            if module is None:
                result.extend(group)
                continue
            # Else flatten the TestInfos in the group to one TestInfo.
            filters = set()
            rel_config = None
            for test_info in group:
                # rel_config should be same for all, so just take last.
                rel_config = test_info.rel_config
                if not test_info.filters:
                    filters = None
                    break
                filters |= test_info.filters
            result.append(TestInfo(rel_config, module, None, filters))
        return result

    def  _parse_build_targets(self, test_info):
        """Parse a list of build targets from a single TestInfo.

        Args:
            test_info: A TestInfo namedtuple.

        Returns:
            A set of strings of the build targets.
        """
        config_file = os.path.join(self.root_dir, test_info.rel_config)
        targets = self._get_targets_from_xml(config_file)
        if os.path.commonprefix(self.gtf_dirs) in test_info.rel_config:
            targets.add('google-tradefed-all')
        else:
            targets.add('tradefed-all')
        if test_info.module_name:
            mod_dir = os.path.dirname(test_info.rel_config).replace('/', '-')
            targets.add(MODULES_IN % mod_dir)
        return targets


    def _generate_build_targets(self, test_infos):
        """Generate a set of build targets for a list of test_infos.

        Args:
            test_infos: A list of TestInfo namedtuples.

        Returns:
            A set of strings of build targets.

        """
        build_targets = set()
        for test_info in test_infos:
            build_targets |= self._parse_build_targets(test_info)
        return build_targets

    def _generate_run_commands(self, test_infos):
        """Generate a list of run commands from TestInfos.

        Args:
            test_infos: A list of TestInfo namedtuples.

        Returns:
            A list of strings of the TradeFederation run commands.
        """
        if logging.getLogger().isEnabledFor(logging.DEBUG):
            log_level = 'VERBOSE'
        else:
            log_level = 'WARN'
        args = ['--log-level', log_level]
        for test_info in test_infos:
            args.extend(['--test-info', test_info.paramify()])
        return [RUN_CMD % ' '.join(args)]

    def _get_test_info(self, test_name, reference_types):
        """Tries to find directory containing test files else returns None

        Args:
            test_name: A string referencing a test.
            reference_types: A list of TetReferenceTypes (ints).

        Returns:
            TestInfo namedtuple, else None if test files not found.
        """
        logging.debug('Finding test for "%s" using reference strategy: %s',
                      test_name, [REFERENCE_TYPE[x] for x in reference_types])
        for ref_type in reference_types:
            ref_name = REFERENCE_TYPE[ref_type]
            try:
                test_info = self.ref_type_to_func_map[ref_type](test_name)
                if test_info:
                    logging.info('Found test for "%s" treating as'
                                 ' %s reference', test_name, ref_name)
                    logging.debug('Resolved "%s" to %s', test_name, test_info)
                    return test_info
                logging.debug('Failed to find %s as %s', test_name, ref_name)
            except KeyError:
                supported = ', '.join(REFERENCE_TYPE[k]
                                      for k in self.ref_type_to_func_map)
                logging.warn('"%s" as %s reference is unsupported. atest only '
                             'supports identifying a test by its: %s',
                             test_name, REFERENCE_TYPE[ref_type],
                             supported)

    def translate(self, tests):
        """Translate atest command line into build targets and run commands.

        Args:
            tests: A list of strings referencing the tests to run.

        Returns:
            A tuple with set of build_target strings and list of run command
            strings.
        """
        logging.info('Finding tests: %s', tests)
        start = time.time()
        test_infos = []
        # TODO: Should we make this also a set to dedupe run cmds? What would a
        # user expect if they listed the same test twice?
        for test in tests:
            possible_reference_types = self._get_test_reference_types(test)
            test_info = self._get_test_info(test, possible_reference_types)
            if not test_info:
                # TODO: Should we raise here, or just stdout a message?
                raise NoTestFoundError('No test found for: %s' % test)
            test_infos.append(test_info)
        test_infos = self._flatten_by_module(test_infos)
        build_targets = self._generate_build_targets(test_infos)
        run_commands = self._generate_run_commands(test_infos)
        end = time.time()
        logging.info('Found tests in %ss', end - start)
        return build_targets, run_commands
