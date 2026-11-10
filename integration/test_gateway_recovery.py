"""Regresion del UP transitorio observado despues de restaurar courier en CI."""
import unittest
from unittest.mock import patch
from verify_stack import wait_for_gateway


class GatewayRecoveryTest(unittest.TestCase):
    def test_does_not_accept_an_up_before_a_pending_down(self):
        states = [(200, {'status': 'UP'}), (503, {'status': 'DOWN'})]
        observed = []

        def health(_):
            state = states.pop(0) if states else (200, {'status': 'UP'})
            observed.append(state[0])
            return state

        with patch('verify_stack.request', side_effect=health):
            wait_for_gateway('http://gateway', timeout=5, stable_seconds=0.02, poll_seconds=0.005)
        self.assertIn(503, observed)
        self.assertGreater(len(observed), 3)

    def test_rejects_a_non_health_endpoint_response(self):
        with patch('verify_stack.request', return_value=(404, {})):
            with self.assertRaisesRegex(AssertionError, 'HTTP 404'):
                wait_for_gateway('http://gateway')


if __name__ == '__main__':
    unittest.main()
