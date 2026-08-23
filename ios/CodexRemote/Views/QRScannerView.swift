import AVFoundation
import CoreImage
import UIKit
import SwiftUI

/// Camera QR scanner. Reports the first payload it decodes and then stops, so
/// a single scan cannot fire the pairing flow repeatedly.
struct QRScannerView: UIViewControllerRepresentable {
    var onScan: (String) -> Void

    func makeCoordinator() -> Coordinator { Coordinator(onScan: onScan) }

    func makeUIViewController(context: Context) -> ScannerController {
        let controller = ScannerController()
        controller.delegate = context.coordinator
        return controller
    }

    func updateUIViewController(_ uiViewController: ScannerController, context: Context) {}

    final class Coordinator: NSObject, AVCaptureMetadataOutputObjectsDelegate {
        private let onScan: (String) -> Void
        private var handled = false

        init(onScan: @escaping (String) -> Void) { self.onScan = onScan }

        func metadataOutput(_ output: AVCaptureMetadataOutput,
                            didOutput metadataObjects: [AVMetadataObject],
                            from connection: AVCaptureConnection) {
            guard !handled,
                  let object = metadataObjects.first as? AVMetadataMachineReadableCodeObject,
                  let value = object.stringValue, !value.isEmpty else { return }
            handled = true
            DispatchQueue.main.async { self.onScan(value) }
        }
    }

    final class ScannerController: UIViewController {
        weak var delegate: AVCaptureMetadataOutputObjectsDelegate?
        private let session = AVCaptureSession()
        private var preview: AVCaptureVideoPreviewLayer?

        override func viewDidLoad() {
            super.viewDidLoad()
            view.backgroundColor = .black
            configure()
        }

        private func configure() {
            guard let device = AVCaptureDevice.default(for: .video),
                  let input = try? AVCaptureDeviceInput(device: device),
                  session.canAddInput(input) else { return }
            session.addInput(input)

            let output = AVCaptureMetadataOutput()
            guard session.canAddOutput(output) else { return }
            session.addOutput(output)
            output.setMetadataObjectsDelegate(delegate, queue: DispatchQueue.main)
            output.metadataObjectTypes = [.qr]

            let layer = AVCaptureVideoPreviewLayer(session: session)
            layer.videoGravity = .resizeAspectFill
            layer.frame = view.bounds
            view.layer.addSublayer(layer)
            preview = layer
        }

        override func viewDidLayoutSubviews() {
            super.viewDidLayoutSubviews()
            preview?.frame = view.bounds
        }

        override func viewWillAppear(_ animated: Bool) {
            super.viewWillAppear(animated)
            guard !session.isRunning else { return }
            // Starting the session blocks; keep it off the main thread.
            DispatchQueue.global(qos: .userInitiated).async { [session] in session.startRunning() }
        }

        override func viewWillDisappear(_ animated: Bool) {
            super.viewWillDisappear(animated)
            if session.isRunning { session.stopRunning() }
        }
    }
}

/// Decodes a QR out of an image the user picked, which is a harder input than a
/// camera frame: terminal screenshots are light-on-dark and often small.
enum QRImageDecoder {
    static func decode(_ image: UIImage) -> String? {
        guard let cgImage = image.cgImage else { return nil }
        let context = CIContext()
        let detector = CIDetector(ofType: CIDetectorTypeQRCode,
                                  context: context,
                                  options: [CIDetectorAccuracy: CIDetectorAccuracyHigh])

        let base = CIImage(cgImage: cgImage)
        if let text = firstPayload(detector, in: base) { return text }

        // Terminal QR is drawn white-on-black; invert and try again.
        if let filter = CIFilter(name: "CIColorInvert") {
            filter.setValue(base, forKey: kCIInputImageKey)
            if let inverted = filter.outputImage,
               let text = firstPayload(detector, in: inverted) {
                return text
            }
        }
        return nil
    }

    private static func firstPayload(_ detector: CIDetector?, in image: CIImage) -> String? {
        guard let features = detector?.features(in: image) as? [CIQRCodeFeature] else { return nil }
        for feature in features {
            if let value = feature.messageString, !value.isEmpty { return value }
        }
        return nil
    }
}
