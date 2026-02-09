"""
Unit tests for the Data Adapter module.
Uses mocked API responses to simulate external dependencies.
"""

import unittest
import json
import os
import sys
import tempfile
from unittest.mock import patch, MagicMock

sys.path.insert(0, os.path.join(os.path.dirname(__file__), '..'))

import requests
from data_adapter import (
    fetch_cognition_data,
    write_raw_data,
    fetch_api_data,
    fetch_user_organization_mappings,
    save_raw_data,
    setup_logging,
)


class TestFetchCognitionDataAuth(unittest.TestCase):
    """Tests for authentication handling in fetch_cognition_data."""

    def test_missing_api_key_raises_value_error(self):
        with patch.dict(os.environ, {}, clear=True):
            if 'DEVIN_ENTERPRISE_API_KEY' in os.environ:
                del os.environ['DEVIN_ENTERPRISE_API_KEY']
            with self.assertRaises(ValueError) as ctx:
                fetch_cognition_data(api_key=None)
            self.assertIn("DEVIN_ENTERPRISE_API_KEY", str(ctx.exception))

    @patch('data_adapter.requests.get')
    def test_401_raises_value_error(self, mock_get):
        mock_response = MagicMock()
        mock_response.status_code = 401
        mock_get.return_value = mock_response

        with self.assertRaises(ValueError) as ctx:
            fetch_cognition_data(api_key="invalid_key")
        self.assertIn("401", str(ctx.exception))

    @patch('data_adapter.requests.get')
    def test_403_raises_value_error(self, mock_get):
        mock_response = MagicMock()
        mock_response.status_code = 403
        mock_get.return_value = mock_response

        with self.assertRaises(ValueError) as ctx:
            fetch_cognition_data(api_key="forbidden_key")
        self.assertIn("403", str(ctx.exception))


class TestFetchCognitionDataPagination(unittest.TestCase):
    """Tests for pagination logic in fetch_cognition_data."""

    @patch('data_adapter.requests.get')
    def test_single_page_response(self, mock_get):
        mock_response = MagicMock()
        mock_response.status_code = 200
        mock_response.json.return_value = {
            'data': [{'id': 1}, {'id': 2}],
            'has_more': False
        }
        mock_response.raise_for_status = MagicMock()
        mock_get.return_value = mock_response

        result = fetch_cognition_data(api_key="test_key", page_size=10)
        self.assertEqual(len(result), 2)
        self.assertEqual(result[0]['id'], 1)
        mock_get.assert_called_once()

    @patch('data_adapter.requests.get')
    def test_multi_page_response(self, mock_get):
        page1 = MagicMock()
        page1.status_code = 200
        page1.json.return_value = {
            'data': [{'id': i} for i in range(5)],
            'has_more': True
        }
        page1.raise_for_status = MagicMock()

        page2 = MagicMock()
        page2.status_code = 200
        page2.json.return_value = {
            'data': [{'id': i} for i in range(5, 8)],
            'has_more': False
        }
        page2.raise_for_status = MagicMock()

        mock_get.side_effect = [page1, page2]

        result = fetch_cognition_data(api_key="test_key", page_size=5)
        self.assertEqual(len(result), 8)
        self.assertEqual(mock_get.call_count, 2)

    @patch('data_adapter.requests.get')
    def test_empty_response(self, mock_get):
        mock_response = MagicMock()
        mock_response.status_code = 200
        mock_response.json.return_value = {
            'data': [],
            'has_more': False
        }
        mock_response.raise_for_status = MagicMock()
        mock_get.return_value = mock_response

        result = fetch_cognition_data(api_key="test_key")
        self.assertEqual(result, [])

    @patch('data_adapter.requests.get')
    def test_date_params_passed_correctly(self, mock_get):
        mock_response = MagicMock()
        mock_response.status_code = 200
        mock_response.json.return_value = {
            'data': [],
            'has_more': False
        }
        mock_response.raise_for_status = MagicMock()
        mock_get.return_value = mock_response

        fetch_cognition_data(
            api_key="test_key",
            start_date="2024-01-01",
            end_date="2024-12-31"
        )

        call_kwargs = mock_get.call_args
        params = call_kwargs.kwargs.get('params', call_kwargs[1].get('params', {}))
        self.assertEqual(params['start_date'], '2024-01-01')
        self.assertEqual(params['end_date'], '2024-12-31')


class TestFetchCognitionDataErrors(unittest.TestCase):
    """Tests for error handling in fetch_cognition_data."""

    @patch('data_adapter.requests.get')
    def test_timeout_raises_exception(self, mock_get):
        mock_get.side_effect = requests.exceptions.Timeout("Connection timed out")

        with self.assertRaises(requests.exceptions.RequestException):
            fetch_cognition_data(api_key="test_key")

    @patch('data_adapter.requests.get')
    def test_connection_error_raises(self, mock_get):
        mock_get.side_effect = requests.exceptions.ConnectionError("Cannot connect")

        with self.assertRaises(requests.exceptions.ConnectionError):
            fetch_cognition_data(api_key="test_key")

    @patch('data_adapter.requests.get')
    def test_http_error_raises(self, mock_get):
        mock_response = MagicMock()
        mock_response.status_code = 500
        mock_response.raise_for_status.side_effect = requests.exceptions.HTTPError("500 Server Error")
        mock_get.return_value = mock_response

        with self.assertRaises(requests.exceptions.HTTPError):
            fetch_cognition_data(api_key="test_key")


class TestWriteRawData(unittest.TestCase):
    """Tests for write_raw_data function."""

    def test_writes_valid_json(self):
        data = [{"id": 1, "value": "test"}, {"id": 2, "value": "test2"}]
        tmp = tempfile.NamedTemporaryFile(
            mode='w', suffix='.json', delete=False
        )
        tmp.close()

        try:
            write_raw_data(data, tmp.name)

            with open(tmp.name, 'r') as f:
                loaded = json.load(f)
            self.assertEqual(loaded, data)
        finally:
            os.unlink(tmp.name)

    def test_writes_empty_list(self):
        tmp = tempfile.NamedTemporaryFile(
            mode='w', suffix='.json', delete=False
        )
        tmp.close()

        try:
            write_raw_data([], tmp.name)

            with open(tmp.name, 'r') as f:
                loaded = json.load(f)
            self.assertEqual(loaded, [])
        finally:
            os.unlink(tmp.name)

    def test_write_to_invalid_path_raises(self):
        with self.assertRaises(Exception):
            write_raw_data([{"test": 1}], '/nonexistent/dir/output.json')


class TestFetchApiData(unittest.TestCase):
    """Tests for fetch_api_data function."""

    @patch('data_adapter.requests.get')
    @patch.dict(os.environ, {'DEVIN_ENTERPRISE_API_KEY': 'test_api_key'})
    def test_successful_multi_endpoint_fetch(self, mock_get):
        mock_response = MagicMock()
        mock_response.status_code = 200
        mock_response.json.return_value = {"data": "test"}
        mock_get.return_value = mock_response

        endpoints = {
            'consumption': '/consumption/daily',
            'members': '/members'
        }

        results = fetch_api_data(endpoints)
        self.assertIn('consumption', results)
        self.assertIn('members', results)
        self.assertEqual(results['consumption']['status_code'], 200)
        self.assertEqual(results['members']['status_code'], 200)

    @patch('data_adapter.requests.get')
    @patch.dict(os.environ, {'DEVIN_ENTERPRISE_API_KEY': 'test_api_key'})
    def test_timeout_endpoint(self, mock_get):
        mock_get.side_effect = requests.exceptions.Timeout("timed out")

        endpoints = {'test': '/test'}
        results = fetch_api_data(endpoints)

        self.assertEqual(results['test']['status_code'], 'TIMEOUT')

    @patch('data_adapter.requests.get')
    @patch.dict(os.environ, {'DEVIN_ENTERPRISE_API_KEY': 'test_api_key'})
    def test_connection_error_endpoint(self, mock_get):
        mock_get.side_effect = requests.exceptions.ConnectionError("refused")

        endpoints = {'test': '/test'}
        results = fetch_api_data(endpoints)

        self.assertEqual(results['test']['status_code'], 'CONNECTION_ERROR')

    @patch('data_adapter.requests.get')
    @patch.dict(os.environ, {'DEVIN_ENTERPRISE_API_KEY': 'test_api_key'})
    def test_non_200_status(self, mock_get):
        mock_response = MagicMock()
        mock_response.status_code = 404
        mock_response.text = "Not Found"
        mock_get.return_value = mock_response

        endpoints = {'test': '/test'}
        results = fetch_api_data(endpoints)

        self.assertEqual(results['test']['status_code'], 404)
        self.assertEqual(results['test']['response'], "Not Found")

    def test_missing_api_key_raises(self):
        with patch.dict(os.environ, {}, clear=True):
            if 'DEVIN_ENTERPRISE_API_KEY' in os.environ:
                del os.environ['DEVIN_ENTERPRISE_API_KEY']
            with self.assertRaises(ValueError):
                fetch_api_data({'test': '/test'})

    @patch('data_adapter.requests.get')
    @patch.dict(os.environ, {'DEVIN_ENTERPRISE_API_KEY': 'test_api_key'})
    def test_audit_logs_uses_different_base_url(self, mock_get):
        mock_response = MagicMock()
        mock_response.status_code = 200
        mock_response.json.return_value = {"data": []}
        mock_get.return_value = mock_response

        endpoints = {'audit': '/audit-logs'}
        fetch_api_data(endpoints)

        call_args = mock_get.call_args
        url = call_args[1].get('url', call_args[0][0] if call_args[0] else '')
        if not url:
            url = call_args.kwargs.get('url', call_args.args[0] if call_args.args else '')
        self.assertIn('/audit-logs', url)
        self.assertNotIn('/enterprise', url)

    @patch('data_adapter.requests.get')
    @patch.dict(os.environ, {'DEVIN_ENTERPRISE_API_KEY': 'test_api_key'})
    def test_params_passed_to_endpoint(self, mock_get):
        mock_response = MagicMock()
        mock_response.status_code = 200
        mock_response.json.return_value = {"data": []}
        mock_get.return_value = mock_response

        endpoints = {'consumption': '/consumption/daily'}
        params = {'consumption': {'start_date': '2024-01-01'}}
        fetch_api_data(endpoints, params_by_endpoint=params)

        call_kwargs = mock_get.call_args
        passed_params = call_kwargs.kwargs.get('params', call_kwargs[1].get('params'))
        self.assertEqual(passed_params['start_date'], '2024-01-01')


class TestFetchUserOrganizationMappings(unittest.TestCase):
    """Tests for fetch_user_organization_mappings function."""

    @patch('data_adapter.requests.get')
    @patch.dict(os.environ, {'DEVIN_ENTERPRISE_API_KEY': 'test_api_key'})
    def test_successful_mapping(self, mock_get):
        mock_response = MagicMock()
        mock_response.status_code = 200
        mock_response.json.return_value = {
            'organizations': [
                {'id': 'org_001', 'name': 'TestOrg'}
            ]
        }
        mock_get.return_value = mock_response

        result = fetch_user_organization_mappings(['user1'])
        self.assertEqual(result['user1']['organization_id'], 'org_001')
        self.assertEqual(result['user1']['organization_name'], 'TestOrg')
        self.assertEqual(result['user1']['status'], 200)

    @patch('data_adapter.requests.get')
    @patch.dict(os.environ, {'DEVIN_ENTERPRISE_API_KEY': 'test_api_key'})
    def test_user_not_found_404(self, mock_get):
        mock_response = MagicMock()
        mock_response.status_code = 404
        mock_get.return_value = mock_response

        result = fetch_user_organization_mappings(['missing_user'])
        self.assertEqual(result['missing_user']['organization_id'], 'Unmapped')
        self.assertEqual(result['missing_user']['status'], 404)

    @patch('data_adapter.requests.get')
    @patch.dict(os.environ, {'DEVIN_ENTERPRISE_API_KEY': 'test_api_key'})
    def test_timeout_handling(self, mock_get):
        mock_get.side_effect = requests.exceptions.Timeout("timed out")

        result = fetch_user_organization_mappings(['user1'])
        self.assertEqual(result['user1']['status'], 'TIMEOUT')
        self.assertEqual(result['user1']['organization_id'], 'Unmapped')

    @patch('data_adapter.requests.get')
    @patch.dict(os.environ, {'DEVIN_ENTERPRISE_API_KEY': 'test_api_key'})
    def test_connection_error_handling(self, mock_get):
        mock_get.side_effect = requests.exceptions.ConnectionError("refused")

        result = fetch_user_organization_mappings(['user1'])
        self.assertEqual(result['user1']['status'], 'CONNECTION_ERROR')

    @patch('data_adapter.requests.get')
    @patch.dict(os.environ, {'DEVIN_ENTERPRISE_API_KEY': 'test_api_key'})
    def test_multiple_users(self, mock_get):
        def side_effect(url, **kwargs):
            resp = MagicMock()
            resp.status_code = 200
            if 'user1' in url:
                resp.json.return_value = {
                    'organizations': [{'id': 'org_A', 'name': 'OrgA'}]
                }
            else:
                resp.json.return_value = {
                    'organizations': [{'id': 'org_B', 'name': 'OrgB'}]
                }
            return resp

        mock_get.side_effect = side_effect

        result = fetch_user_organization_mappings(['user1', 'user2'])
        self.assertEqual(len(result), 2)
        self.assertEqual(result['user1']['organization_name'], 'OrgA')
        self.assertEqual(result['user2']['organization_name'], 'OrgB')

    @patch('data_adapter.requests.get')
    @patch.dict(os.environ, {'DEVIN_ENTERPRISE_API_KEY': 'test_api_key'})
    def test_list_response_format(self, mock_get):
        mock_response = MagicMock()
        mock_response.status_code = 200
        mock_response.json.return_value = [
            {'id': 'org_list', 'name': 'ListOrg'}
        ]
        mock_get.return_value = mock_response

        result = fetch_user_organization_mappings(['user1'])
        self.assertEqual(result['user1']['organization_id'], 'org_list')
        self.assertEqual(result['user1']['organization_name'], 'ListOrg')

    @patch('data_adapter.requests.get')
    @patch.dict(os.environ, {'DEVIN_ENTERPRISE_API_KEY': 'test_api_key'})
    def test_direct_dict_response(self, mock_get):
        mock_response = MagicMock()
        mock_response.status_code = 200
        mock_response.json.return_value = {
            'id': 'org_direct',
            'name': 'DirectOrg'
        }
        mock_get.return_value = mock_response

        result = fetch_user_organization_mappings(['user1'])
        self.assertEqual(result['user1']['organization_id'], 'org_direct')

    def test_missing_api_key_raises(self):
        with patch.dict(os.environ, {}, clear=True):
            if 'DEVIN_ENTERPRISE_API_KEY' in os.environ:
                del os.environ['DEVIN_ENTERPRISE_API_KEY']
            with self.assertRaises(ValueError):
                fetch_user_organization_mappings(['user1'])

    @patch('data_adapter.requests.get')
    @patch.dict(os.environ, {'DEVIN_ENTERPRISE_API_KEY': 'test_api_key'})
    def test_empty_user_list(self, mock_get):
        result = fetch_user_organization_mappings([])
        self.assertEqual(result, {})
        mock_get.assert_not_called()


class TestSaveRawData(unittest.TestCase):
    """Tests for save_raw_data function."""

    def test_saves_dict_to_json(self):
        data = {'key': 'value', 'nested': {'a': 1}}
        tmp = tempfile.NamedTemporaryFile(
            mode='w', suffix='.json', delete=False
        )
        tmp.close()

        try:
            save_raw_data(data, tmp.name)

            with open(tmp.name, 'r') as f:
                loaded = json.load(f)
            self.assertEqual(loaded, data)
        finally:
            os.unlink(tmp.name)

    def test_save_to_invalid_path_raises(self):
        with self.assertRaises(Exception):
            save_raw_data({'test': 1}, '/nonexistent/dir/output.json')


class TestSetupLogging(unittest.TestCase):
    """Tests for setup_logging function."""

    def test_setup_logging_configures_handlers(self):
        setup_logging()
        import logging
        root = logging.getLogger()
        handler_types = [type(h).__name__ for h in root.handlers]
        self.assertIn('FileHandler', handler_types)
        self.assertIn('StreamHandler', handler_types)

        for h in root.handlers[:]:
            if isinstance(h, logging.FileHandler):
                h.close()
                root.removeHandler(h)

        if os.path.exists('finops_pipeline.log'):
            os.unlink('finops_pipeline.log')


if __name__ == '__main__':
    unittest.main()
